/*
 * Copyright 2017-2023 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.control.control;


import com.typesafe.config.Config;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;
import org.polypheny.control.httpinterface.ClientCommunicationStream;
import org.polypheny.control.main.NotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Slf4j
public class ServiceManager {

    private static final Object MUTEX = new Object();
    private static PolyphenyDbProcess polyphenyDbProcess = null; // ! Shared over multiple stateless requests

    private static final ExecutorService PROCESS_OUTPUT_REDIRECTOR_EXECUTOR = Executors.newFixedThreadPool( 2 );
    private static Tailer logTailer = null; // ! Shared over multiple stateless requests
    private static Tailer errTailer = null; // ! Shared over multiple stateless requests

    private static boolean currentlyUpdating = false;
    public static boolean polyfierMode = false;

    static {
        Runtime.getRuntime().addShutdownHook( new Thread( () -> {
            if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
                polyphenyDbProcess.kill();
            }
        } ) );
    }


    /**
     * This block restores the PolyphenyDbProcess on startup by checking the PID file. It will create a PolyphenyDbProcess
     * from the PID if the file contains a PID number. Then it will check if the process is still alive.
     *
     * TODO: This is maybe not required since we start a child process. By the termination of this process usually the child
     *  processes are terminated too. However, if later on there is another way of creating the Polypheny-DB process this
     *  static block would be more relevant (I guess).
     */
    private static void restorePolyphenyDbProcess() {
        Config configuration = ConfigManager.getConfig();
        File workingDir = new File( configuration.getString( "pcrtl.workingdir" ) );
        if ( !workingDir.exists() ) {
            if ( !workingDir.mkdirs() ) {
                throw new RuntimeException( "Could not create the required directories: " + workingDir );
            }
            if ( SystemUtils.IS_OS_WINDOWS ) {
                try {
                    Files.setAttribute( workingDir.toPath(), "dos:hidden", true );
                } catch ( IOException e ) {
                    log.info( "IOException while setting the hidden flag on the working directory.", e );
                }
            }
        }

        File pidFile = new File( configuration.getString( "pcrtl.pdbms.pidfile" ) );
        try {
            FileUtils.touch( pidFile );
            if ( pidFile.exists() ) {
                // should always be true...

                try ( BufferedReader pidReader = new BufferedReader( new InputStreamReader( new FileInputStream( pidFile ), StandardCharsets.UTF_8 ) ) ) {
                    String line = pidReader.readLine();
                    if ( line != null && !line.isEmpty() ) {
                        // Restore
                        polyphenyDbProcess = PolyphenyDbProcess.createFromPid( Integer.parseInt( line ) );
                    }
                } catch ( FileNotFoundException e ) {
                    log.error( "File exists but not found?!", e );
                } catch ( IOException e ) {
                    log.error( "IOException while recovering the PID.", e );
                }

                if ( polyphenyDbProcess != null && !polyphenyDbProcess.isAlive() ) {
                    // if dead, make sure the file is empty
                    try ( OutputStreamWriter pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile ), StandardCharsets.UTF_8 ) ) {
                        pidWriter.append( "" );
                        pidWriter.flush();
                    } catch ( IOException e ) {
                        log.error( "IOException while deleting the stored PID of a dead process.", e );
                    }
                }
            }
        } catch ( IOException e ) {
            log.error( "IOException while touching the PID file.", e );
        }
    }


    public static boolean start( final ClientCommunicationStream clientCommunicationStream ) {
        return start( clientCommunicationStream, true );
    }


    public static boolean start( final ClientCommunicationStream clientCommunicationStream, final boolean startTailers ) {
        return start( clientCommunicationStream, true, "" );
    }


    public static boolean start( final ClientCommunicationStream clientCommunicationStream, final boolean startTailers, String additionalArguments ) {
        Config configuration = ConfigManager.getConfig();
        synchronized ( MUTEX ) {
            //restorePolyphenyDbProcess();

            if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Polypheny-DB is already running. Stop it first or use the restart function." );
                }
                NotificationManager.error( "Polypheny-DB is already running!" );
                log.warn( "> Polypheny-DB is already running. Stop it first or use the restart function." );
                return false;
            }

            String workingDir = configuration.getString( "pcrtl.workingdir" );
            String pidFile = configuration.getString( "pcrtl.pdbms.pidfile" );
            String logsDir = configuration.getString( "pcrtl.logsdir" );
            String javaExecutable = configuration.getString( "pcrtl.java.executable" ) + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
            List<String> javaOptions = configuration.getStringList( "pcrtl.java.options" );
            String javaMaximumHeapSize = configuration.getString( "pcrtl.java.heap" );
            String pdbmsJar = configuration.getString( "pcrtl.pdbms.jarfile" );
            String pdbmsMainClass = configuration.getString( "pcrtl.pdbms.mainclass" );
            String pdbmsArgs = configuration.getString( "pcrtl.pdbms.args" );
            String logFile = new File( new File( logsDir ), new SimpleDateFormat( "'polypheny-db_'yyyy.MM.dd_HH-mm-ss.SSS'.log'" ).format( new Date() ) ).getAbsolutePath();
            String errFile = logFile.substring( 0, logFile.lastIndexOf( '.' ) ) + ".err.log";
            //
            //

            LinkedList<String> javaOptionsFull = new LinkedList<>( javaOptions );
            String applicationConfFileName = new File( new File( workingDir ), "config" ).getAbsolutePath() + File.separator + "application.conf";
            if ( new File( applicationConfFileName ).exists() ) {
                javaOptionsFull.addFirst( "-Dconfig.file=" + applicationConfFileName );
            }
            javaOptionsFull.addFirst( "-Xmx" + javaMaximumHeapSize + "G" );

            // Build list of arguments
            List<String> pdbArguments = new LinkedList<>();
            if ( pdbmsArgs.trim().length() > 0 ) {
                pdbArguments.addAll( Arrays.asList( pdbmsArgs.split( " " ) ) );
            }
            if ( additionalArguments.trim().length() > 0 ) {
                pdbArguments.addAll( Arrays.asList( additionalArguments.split( " " ) ) );
            }
            if ( configuration.getString( "pcrtl.plugins.purge" ).equals( "onStartup" ) ) {
                pdbArguments.add( "-resetPlugins" );
            }

            if ( !new File( javaExecutable ).exists() ) {
                throw new RuntimeException( "The java executable seems not to exist... How did you start this application?!" );
            }

            if ( !new File( pdbmsJar ).exists() ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> There is no Polypheny-DB jar file. Trigger an update first." );
                }
                log.warn( "> There is no Polypheny-DB jar file. Trigger an update first." );
                NotificationManager.error( "There is no Polypheny-DB JAR file. You need to trigger an update on the dashboard." );
                return false;
            }

            if ( !new File( logsDir ).exists() ) {
                if ( !new File( logsDir ).mkdirs() ) {
                    throw new RuntimeException( "Could not create the logs directory." );
                }
            }

            try ( OutputStreamWriter pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile, false ), StandardCharsets.UTF_8 ) ) {

                log.info( "> Starting Polypheny-DB" );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Starting Polypheny-DB" );
                }

                polyphenyDbProcess = PolyphenyDbProcessBuilder.builder()
                        .withWorkingDir( new File( workingDir ) )
                        .withJavaExecutable( new File( javaExecutable ) )
                        .withJavaOptions( javaOptionsFull.toArray( new String[0] ) )
                        .withClassPath(
                                new File( pdbmsJar ).getAbsolutePath()
                                //new File( new File( workingDir ), "config" ).getAbsolutePath() + File.separator + "*",
                                //new File( new File( workingDir ), "plugins" ).getAbsolutePath() + File.separator + "*"
                        )
                        .withMainClass( pdbmsMainClass )
                        .withArguments( pdbArguments.toArray( new String[0] ) )
                        .withLogFile( new File( logFile ), false )
                        .withErrFile( new File( errFile ), false )
                        .start();

                int polyphenyDbProcessId = polyphenyDbProcess.getPid();
                pidWriter.append( String.valueOf( polyphenyDbProcessId ) );
                pidWriter.flush();

                log.info( "> PID = {}", polyphenyDbProcessId );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> PID = " + polyphenyDbProcessId );
                }

                // Create logger
                final Logger PDB_LOGGER = LoggerFactory.getLogger( "PDB" );

                if ( logTailer != null ) {
                    logTailer.stop();
                }
                if ( clientCommunicationStream != null ) {
                    logTailer = new Tailer( new File( logFile ), new LogTailerListener( PDB_LOGGER::info, clientCommunicationStream::send ) );
                } else {
                    logTailer = new Tailer( new File( logFile ), new LogTailerListener( PDB_LOGGER::info ) );
                }

                if ( errTailer != null ) {
                    errTailer.stop();
                }
                if ( clientCommunicationStream != null ) {
                    errTailer = new Tailer( new File( errFile ), new LogTailerListener( PDB_LOGGER::info, clientCommunicationStream::send ) );
                } else {
                    errTailer = new Tailer( new File( errFile ), new LogTailerListener( PDB_LOGGER::info ) );
                }

                if ( startTailers ) {
                    PROCESS_OUTPUT_REDIRECTOR_EXECUTOR.submit( logTailer );
                    PROCESS_OUTPUT_REDIRECTOR_EXECUTOR.submit( errTailer );
                }

                log.info( "> ... done." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> ... done." );
                }

                return true;
            } catch ( IOException ex ) {
                log.error( "Caught exception while starting Polypheny-DB", ex );
                return false;
            }
        }
    }


    public static boolean stop( final ClientCommunicationStream clientCommunicationStream ) {
        Config configuration = ConfigManager.getConfig();
        synchronized ( MUTEX ) {
            //restorePolyphenyDbProcess();

            if ( polyphenyDbProcess == null ) {
                // NO-OP if there is no process running
                return true;
            } else if ( polyphenyDbProcess.isAlive() ) {
                log.info( "> Stopping Polypheny-DB ..." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Stopping Polypheny-DB ..." );
                }
                polyphenyDbProcess.kill();
            } else {
                // already terminated
            }

            //
            File pidFile = new File( configuration.getString( "pcrtl.pdbms.pidfile" ) );
            //

            polyphenyDbProcess = null;

            // Stopping std out redirectors
            if ( logTailer != null ) {
                logTailer.stop();
            }
            logTailer = null;
            if ( errTailer != null ) {
                errTailer.stop();
            }
            errTailer = null;

            if ( !pidFile.delete() ) {
                try ( OutputStreamWriter pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile, false ), StandardCharsets.UTF_8 ) ) {
                    pidWriter.append( "" );
                    pidWriter.flush();
                } catch ( IOException e ) {
                    log.error( "Could not delete the PID.", e );
                }
            }
            // delete or emptying was successful

            log.info( "> ... done." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> ... done." );
            }

            return true;
        }
    }


    public static boolean restart( final ClientCommunicationStream clientCommunicationStream ) {
        return restart( clientCommunicationStream, true );
    }


    public static boolean restart( final ClientCommunicationStream clientCommunicationStream, final boolean startTailers ) {
        synchronized ( MUTEX ) {
            stop( clientCommunicationStream );
            return start( clientCommunicationStream, startTailers );
        }
    }


    public static boolean update( final ClientCommunicationStream clientCommunicationStream ) {
        Config configuration = ConfigManager.getConfig();
        synchronized ( MUTEX ) {
            try {
                currentlyUpdating = true;
                //restorePolyphenyDbProcess();
                if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
                    // polypheny-db process running
                    log.info( "> Stop Polypheny-DB first before updating it." );
                    if ( clientCommunicationStream != null ) {
                        clientCommunicationStream.send( "> Stop Polypheny-DB first before updating it." );
                    }
                    NotificationManager.error( "Stop Polypheny-DB first before updating it." );
                    return false;
                }

                String workingDir = configuration.getString( "pcrtl.workingdir" );
                String builddir = configuration.getString( "pcrtl.builddir" );

                if ( !new File( workingDir ).exists() ) {
                    if ( !new File( workingDir ).mkdirs() ) {
                        throw new RuntimeException( "Could not create the folders for " + new File( workingDir ).getAbsolutePath() );
                    }
                }

                String buildMode = configuration.getString( "pcrtl.buildmode" );
                String cleanMode = configuration.getString( "pcrtl.clean.mode" );
                boolean clean = false;
                if ( cleanMode.equals( "always" ) ) {
                    clean = true;
                } else if ( cleanMode.equals( "branchChange" ) ) {
                    Map<String, String> versions = getVersion();
                    if ( !versions.get( "pdb-branch" ).equals( configuration.getString( "pcrtl.pdbms.branch" ) ) ) {
                        clean = true;
                    }
                    if ( !buildMode.equals( "pdb" ) && !versions.get( "pui-branch" ).equals( configuration.getString( "pcrtl.ui.branch" ) ) ) {
                        clean = true;
                    }
                }
                if ( clean ) {
                    log.info( "> Deleting build folder ..." );
                    if ( clientCommunicationStream != null ) {
                        clientCommunicationStream.send( "> Deleting build folder ..." );
                    }

                    try {
                        FileUtils.deleteDirectory( new File( builddir ) );
                        log.info( "> Deleting build folder ... Done." );
                        if ( clientCommunicationStream != null ) {
                            clientCommunicationStream.send( "> Deleting build folder ... Done." );
                        }
                    } catch ( IOException e ) {
                        log.warn( "Could not delete build folder!", e );
                        if ( clientCommunicationStream != null ) {
                            clientCommunicationStream.send( "> Deleting build folder ... Could not delete the build directory." );
                            throw new RuntimeException( "Could not delete build folder " + new File( builddir ).getAbsolutePath() );
                        }
                    }
                }

                if ( !new File( builddir ).exists() ) {
                    if ( !new File( builddir ).mkdirs() ) {
                        throw new RuntimeException( "Could not create the folders for " + new File( builddir ).getAbsolutePath() );
                    }
                }

                log.info( "> Updating Polypheny ..." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Updating Polypheny-DB ..." );
                }

                boolean installedUI = false;
                if ( buildMode.equals( "both" ) || buildMode.equals( "pui" ) ) {
                    installedUI = installUi( clientCommunicationStream, configuration );
                }
                if ( buildMode.equals( "both" ) || buildMode.equals( "pdb" ) || installedUI ) {
                    buildPdb( clientCommunicationStream, configuration, installedUI );
                }

                if ( clientCommunicationStream != null ) {
                    log.info( "> Updating Polypheny ... finished." );
                    clientCommunicationStream.send( "********************************************************" );
                    clientCommunicationStream.send( "         Polypheny has successfully been built!" );
                    clientCommunicationStream.send( "********************************************************" );
                    NotificationManager.info( "Polypheny-DB has successfully been built!" );
                }
                return true;
            } finally {
                currentlyUpdating = false;
            }
        }
    }


    private static void buildPdb( final ClientCommunicationStream clientCommunicationStream, Config configuration, boolean forceUiBuild ) {
        boolean requiresBuild = false;

        File pdbBuildDir = new File( configuration.getString( "pcrtl.pdbbuilddir" ) );
        String repo = configuration.getString( "pcrtl.pdbms.repo" );
        String branch = configuration.getString( "pcrtl.pdbms.branch" );

        // Delete old DBMS Jar
        File oldJar = new File( configuration.getString( "pcrtl.pdbms.oldjarfile" ) );
        if ( oldJar.exists() ) {
            if ( !oldJar.delete() ) {
                log.info( "> Unable to delete the old jar file." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Unable to delete the old jar file." );
                }
            }
        }

        // Rename DBMS Jar
        File jar = new File( configuration.getString( "pcrtl.pdbms.jarfile" ) );
        if ( jar.exists() ) {
            if ( !jar.renameTo( oldJar ) ) {
                log.info( "> Unable to rename the jar file." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Unable to rename the jar file." );
                }
            }
        }

        try {
            if ( pdbBuildDir.exists() ) {
                Git git = Git.open( pdbBuildDir );
                if ( !validateGitRepository( git.getRepository() ) ) {
                    if ( !pdbBuildDir.delete() ) {
                        throw new RuntimeException( "Unable to delete invalid PDB build folder" );
                    }
                    clonePdbRepository( clientCommunicationStream, configuration );
                    requiresBuild = true;
                }
                git.close();
            } else {
                clonePdbRepository( clientCommunicationStream, configuration );
                requiresBuild = true;
            }
        } catch ( IOException e ) {
            throw new RuntimeException( "Exception while cloning and validating pdb repo", e );
        }

        log.info( "> Pulling Polypheny-DB repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-DB repository ..." );
        }
        try {
            Git git = Git.open( pdbBuildDir );
            if ( !existsRemoteBranchWithName( git, branch ) ) {
                throw new RuntimeException( "There is no branch with the name " + branch + " on remote of the Polypheny-DB repo!" );
            }
            String oldCommitId;
            try {
                oldCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            } catch ( NullPointerException e ) {
                // This e.g. happens if the working copy is in an invalid state (not the repo itself)
                oldCommitId = "-1";
                requiresBuild = true;
            }
            if ( !existsLocalBranchWithName( git, branch ) ) {
                git.branchCreate()
                        .setName( branch )
                        .setUpstreamMode( SetupUpstreamMode.SET_UPSTREAM )
                        .setStartPoint( "origin/" + branch )
                        .setForce( true )
                        .call();
            }
            git.checkout().setName( branch ).call();
            git.pull().call();
            String newCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            requiresBuild |= !oldCommitId.equals( newCommitId );
            git.close();
        } catch ( GitAPIException | IOException e ) {
            throw new RuntimeException( "Exception while pulling Polypheny-DB repo", e );
        }
        log.info( "> Pulling Polypheny-DB repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-DB repository ... finished." );
        }

        // Check if we need to build
        if ( !requiresBuild && !forceUiBuild && oldJar.exists() ) {
            log.info( "> No changes to PDB repository and therefore no need to rebuild Polypheny-DB ..." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> No changes to PDB repository and therefore no need to rebuild Polypheny-DB ..." );
            }
            // Restore old jar file
            if ( oldJar.exists() ) {
                if ( !oldJar.renameTo( jar ) ) {
                    log.info( "> Unable to restore to the old jar file." );
                    if ( clientCommunicationStream != null ) {
                        clientCommunicationStream.send( "> Unable to restore to the old jar file." );
                    }
                }
            }
            return;
        }

        if ( forceUiBuild ) {
            log.info( "> Force updating Polypheny-UI ..." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> Force updating Polypheny-UI ..." );
            }
            try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( pdbBuildDir ).connect() ) {
                BuildLauncher buildLauncher = connection.newBuild()
                        .setStandardOutput( null )
                        .setStandardError( System.err )
                        .forTasks( ":webui:clean" );
                buildLauncher.run();
            }
            log.info( "> Force updating Polypheny-UI ... finished." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> Force updating Polypheny-UI ... finished." );
            }
        }

        // Build
        log.info( "> Building Polypheny-DB ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Building Polypheny-DB ..." );
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( pdbBuildDir ).connect() ) {
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( null )
                    .setStandardError( System.err )
                    .forTasks( "build" )
                    .withArguments( "-x", "test", "-x", "licensee" );

            if ( clientCommunicationStream != null ) {
                buildLauncher.addProgressListener( event -> clientCommunicationStream.send( event.getDisplayName() ), OperationType.TASK );
            }
            buildLauncher.run();
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( pdbBuildDir ).connect() ) {
            // Build shadow jar again to include plugins (not a nice solution, but it takes time till this is fixed on all branches)
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( null )
                    .setStandardError( System.err )
                    .forTasks( "dbms:shadowJar" )
                    .withArguments( "-x", "test", "-x", "licensee" );

            if ( clientCommunicationStream != null ) {
                buildLauncher.addProgressListener( event -> clientCommunicationStream.send( event.getDisplayName() ), OperationType.TASK );
            }
            buildLauncher.run();
        }

        log.info( "> Building Polypheny-DB ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Building Polypheny-DB ... finished." );
        }

        // Move jar to working dir
        File dbmsJarFolder = new File( pdbBuildDir, "dbms" + File.separator + "build" + File.separator + "libs" );
        File[] files = dbmsJarFolder.listFiles( ( dir, name ) -> name.startsWith( "dbms-" ) );

        File dbmsJar = null;
        if ( files != null ) {
            int applicableFileCounter = 0;
            for ( File f : files ) {
                if ( !f.getName().contains( "javadoc" ) && !f.getName().contains( "sources" ) && !f.getName().contains( "test" ) ) {
                    dbmsJar = f;
                    applicableFileCounter++;
                }
            }
            if ( applicableFileCounter > 1 ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> There are multiple applicable JAR files in the dbms/build/libs folder!" );
                }
                throw new RuntimeException( "There are multiple applicable JAR files in the dbms/build/libs folder!" );
            }
        } else {
            throw new RuntimeException( "JAR file not found!" );
        }

        if ( dbmsJar != null && dbmsJar.exists() ) {
            if ( !dbmsJar.renameTo( jar ) ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Unable to move JAR file" );
                }
                throw new RuntimeException( "Unable to move JAR file" );
            }
        } else {
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> JAR file does not exist!" );
            }
            throw new RuntimeException( "JAR file does not exist!" );
        }

        // Purge plugins dir
        if ( configuration.getString( "pcrtl.plugins.purge" ).equals( "afterBuild" ) ) {
            File pluginsFolder = new File( configuration.getString( "pcrtl.pdbms.pluginsdir" ) );
            if ( pluginsFolder.exists() ) {
                if ( !pluginsFolder.delete() ) {
                    if ( clientCommunicationStream != null ) {
                        clientCommunicationStream.send( "> Unable to purge folder!" );
                    }
                    throw new RuntimeException( "Unable to purge folder!" );
                }
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Purged Polypheny plugins folder!" );
                }
            }
        }
    }


    public static void clonePdbRepository( ClientCommunicationStream clientCommunicationStream, Config configuration ) {
        File pdbBuildDir = new File( configuration.getString( "pcrtl.pdbbuilddir" ) );
        String repo = configuration.getString( "pcrtl.pdbms.repo" );

        log.info( "> Cloning Polypheny-DB repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-DB repository ..." );
        }
        try {
            final Git git = Git.cloneRepository()
                    .setURI( repo )
                    .setDirectory( pdbBuildDir )
                    .setBranch( "master" )
                    .call();
            git.close();
        } catch ( GitAPIException e ) {
            throw new RuntimeException( "Exception while cloning Polypheny-DB repo", e );
        }
        log.info( "> Cloning Polypheny-DB repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-DB repository ... finished." );
        }
    }


    // return value indicated whether the repo has been installed (false if there haven't been any changes)
    private static boolean installUi( final ClientCommunicationStream clientCommunicationStream, Config configuration ) {
        boolean requiresInstall = false;

        String buildDir = configuration.getString( "pcrtl.builddir" );
        String repo = configuration.getString( "pcrtl.ui.repo" );
        String branch = configuration.getString( "pcrtl.ui.branch" );

        File uiBuildDir = new File( buildDir, "ui" );

        try {
            if ( uiBuildDir.exists() ) {
                Git git = Git.open( uiBuildDir );
                if ( !validateGitRepository( git.getRepository() ) ) {
                    if ( !uiBuildDir.delete() ) {
                        throw new RuntimeException( "Unable to delete invalid UI build folder" );
                    }
                    clonePuiRepository( clientCommunicationStream, configuration );
                    requiresInstall = true;
                }
                git.close();
            } else {
                clonePuiRepository( clientCommunicationStream, configuration );
                requiresInstall = true;
            }
        } catch ( IOException e ) {
            throw new RuntimeException( "Exception while cloning and validating pui repo", e );
        }

        // Pull the repository
        log.info( "> Pulling Polypheny-UI repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-UI repository ..." );
        }
        try {
            Git git = Git.open( uiBuildDir );
            if ( !existsRemoteBranchWithName( git, branch ) ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> There is no branch with the name " + branch + " on remote of the Polypheny-UI repo! You can change the repo in the settings." );
                }
                throw new RuntimeException( "There is no branch with the name " + branch + " on remote of the Polypheny-UI repo!" );
            }
            String oldCommitId;
            try {
                oldCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            } catch ( NullPointerException e ) {
                // This e.g. happens if the working copy is in an invalid state (not the repo itself)
                oldCommitId = "-1";
                requiresInstall = true;
            }
            if ( !existsLocalBranchWithName( git, branch ) ) {
                git.branchCreate()
                        .setName( branch )
                        .setUpstreamMode( SetupUpstreamMode.SET_UPSTREAM )
                        .setStartPoint( "origin/" + branch )
                        .setForce( true )
                        .call();
            }
            git.checkout().setName( branch ).call();
            git.pull().call();
            String newCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            requiresInstall |= !oldCommitId.equals( newCommitId );
            git.close();
        } catch ( GitAPIException | IOException e ) {
            throw new RuntimeException( "Exception while cloning Polypheny-DB repo", e );
        }
        log.info( "> Pulling Polypheny-UI repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-UI repository ... finished." );
        }

        // Check if we need to build
        File jar = new File( configuration.getString( "pcrtl.pdbms.jarfile" ) );
        if ( !requiresInstall && jar.exists() ) {
            log.info( "> No changes to UI repository and therefore no need to rebuild Polypheny-UI ..." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> No changes to UI repository and therefore no need to rebuild Polypheny-UI ..." );
            }
            return false;
        }

        // Clean
        log.info( "> Cleaning Polypheny-UI ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cleaning Polypheny-UI ..." );
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( uiBuildDir ).connect() ) {
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( null )
                    .setStandardError( System.err )
                    .forTasks( "clean" );
            buildLauncher.run();
        }
        log.info( "> Cleaning Polypheny-UI ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cleaning Polypheny-UI ... finished." );
        }

        // Install
        log.info( "> Installing Polypheny-UI ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Installing Polypheny-UI ..." );
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( uiBuildDir ).connect() ) {
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( null )
                    .setStandardError( System.err )
                    .forTasks( "install" )
                    .withArguments( "-x", "test" );

            if ( clientCommunicationStream != null ) {
                buildLauncher.addProgressListener( event -> clientCommunicationStream.send( event.getDisplayName() ), OperationType.TASK );
            }
            buildLauncher.run();
        }
        log.info( "> Installing Polypheny-UI ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Installing Polypheny-UI ... finished." );
        }
        return true;
    }


    public static void clonePuiRepository( ClientCommunicationStream clientCommunicationStream, Config configuration ) {
        String buildDir = configuration.getString( "pcrtl.builddir" );
        String repo = configuration.getString( "pcrtl.ui.repo" );
        File uiBuildDir = new File( buildDir, "ui" );

        log.info( "> Cloning Polypheny-UI repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-UI repository ..." );
        }
        try {
            final Git git = Git.cloneRepository()
                    .setURI( repo )
                    .setDirectory( uiBuildDir )
                    .setBranch( "master" )
                    .call();
            git.close();
        } catch ( GitAPIException e ) {
            throw new RuntimeException( "Exception while cloning Polypheny-UI repo", e );
        }
        log.info( "> Cloning Polypheny-UI repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-UI repository ... finished." );
        }
    }


    public static Map<String, String> getVersion() {
        Config configuration = ConfigManager.getConfig();
        String buildDir = configuration.getString( "pcrtl.builddir" );
        File pdbBuildDir = new File( buildDir, "pdb" );
        File puiBuildDir = new File( buildDir, "ui" );

        Map<String, String> map = new HashMap<>();

        // Get PDB branch and commit
        try {
            Git git = null;
            if ( pdbBuildDir.exists() ) {
                git = Git.open( pdbBuildDir );
                if ( !validateGitRepository( git.getRepository() ) ) {
                    git.close();
                    git = null;
                }
            }

            if ( git != null ) {
                git.fetch().call();
                map.put( "pdb-branch", git.getRepository().getBranch() );
                if ( git.getRepository().resolve( Constants.HEAD ) != null ) {
                    map.put( "pdb-commit", git.getRepository().resolve( Constants.HEAD ).getName() );
                    map.put( "pdb-behind", "" + BranchTrackingStatus.of( git.getRepository(), git.getRepository().getBranch() ).getBehindCount() );
                } else {
                    map.put( "pdb-commit", "--------" );
                    map.put( "pdb-behind", "0" );
                }
                git.close();
            } else {
                map.put( "pdb-branch", "Unknown" );
                map.put( "pdb-commit", "--------" );
                map.put( "pdb-behind", "0" );
            }
        } catch ( IOException | GitAPIException e ) {
            if ( currentlyUpdating ) {
                // ignore exceptions while updating repo
            } else {
                log.error( "Error while retrieving pdb version", e );
            }
        }

        // Get PUI branch and commit
        try {
            Git git = null;
            if ( puiBuildDir.exists() ) {
                git = Git.open( puiBuildDir );
                if ( !validateGitRepository( git.getRepository() ) ) {
                    git.close();
                    git = null;
                }
            }

            if ( git != null ) {
                git.fetch().call();
                map.put( "pui-branch", git.getRepository().getBranch() );
                if ( git.getRepository().resolve( Constants.HEAD ) != null ) {
                    map.put( "pui-commit", git.getRepository().resolve( Constants.HEAD ).getName() );
                    map.put( "pui-behind", "" + BranchTrackingStatus.of( git.getRepository(), git.getRepository().getBranch() ).getBehindCount() );
                } else {
                    map.put( "pui-commit", "--------" );
                    map.put( "pui-behind", "0" );
                }
                git.close();
            } else {
                map.put( "pui-branch", "Unknown" );
                map.put( "pui-commit", "--------" );
                map.put( "pui-behind", "0" );
            }
        } catch ( IOException | GitAPIException e ) {
            if ( currentlyUpdating ) {
                // ignore exceptions while updating repo
            } else {
                log.error( "Error while retrieving pui version", e );
            }
        }

        return map;
    }


    public static Object getStatus() {
        if ( polyfierMode ) {
            return "polyfier";
        } else if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
            return "running";
        } else if ( currentlyUpdating ) {
            return "updating";
        } else {
            return "idling";
        }
    }


    private static boolean existsLocalBranchWithName( Git git, String branchName ) throws GitAPIException {
        List<Ref> branches = git.branchList().call();
        for ( Ref ref : branches ) {
            if ( ref.getName().equals( "refs/heads/" + branchName ) ) {
                return true;
            }
        }
        return false;
    }


    private static boolean existsRemoteBranchWithName( Git git, String branchName ) throws GitAPIException {
        List<Ref> branches = git.branchList().setListMode( ListMode.REMOTE ).call();
        for ( Ref ref : branches ) {
            if ( ref.getName().equals( "refs/remotes/origin/" + branchName ) ) {
                return true;
            }
        }
        return false;
    }


    private static boolean validateGitRepository( Repository repo ) {
        try {
            if ( !RepositoryCache.FileKey.isGitRepository( repo.getDirectory(), FS.DETECTED ) ) {
                return false;
            }
            for ( Ref ref : repo.getRefDatabase().getRefs() ) {
                if ( ref.getObjectId() == null ) {
                    continue;
                }
                return true;
            }
            return false;
        } catch ( IOException e ) {
            log.error( "Exception while validating repo", e );
            return false;
        }
    }


    public static List<String> getAvailableBranches( File repo ) {
        List<String> branches = new LinkedList<>();
        try {
            Git git = Git.open( repo );
            List<Ref> branchRefs = git.branchList().setListMode( ListMode.REMOTE ).call();
            for ( Ref ref : branchRefs ) {
                String name = ref.getName();
                // Remove "refs/remotes/origin/"
                name = name.replace( "refs/remotes/origin/", "" );
                branches.add( name );
            }
            git.close();
        } catch ( GitAPIException | IOException e ) {
            log.error( "Exception while getting list of branches", e );
        }
        return branches;
    }


    /**
     *
     */
    private static class LogTailerListener implements TailerListener {

        private final List<Consumer<String>> consumers;
        private Tailer tailer;


        @SafeVarargs
        LogTailerListener( @NonNull final Consumer<String>... consumers ) {
            this.consumers = new ArrayList<>();
            this.consumers.addAll( Arrays.asList( consumers ) );
        }


        @Override
        public void init( Tailer tailer ) {
            this.tailer = tailer;
        }


        @Override
        public void fileNotFound() {
            if ( this.tailer.getFile().getName().endsWith( ".err.log" ) ) {
                for ( Consumer<String> consumer : consumers ) {
                    consumer.accept( "> !! The error log file was not found. Stopping the Tailer. !!" );
                }
            } else {
                for ( Consumer<String> consumer : consumers ) {
                    consumer.accept( "> !! The log file was not found. Stopping the Tailer. !!" );
                }
            }
            this.tailer.stop();
        }


        @Override
        public void fileRotated() {
            if ( this.tailer.getFile().getName().endsWith( ".err.log" ) ) {
                for ( Consumer<String> consumer : consumers ) {
                    consumer.accept( "> !! The current error log file has rotated !!" );
                }
            } else {
                for ( Consumer<String> consumer : consumers ) {
                    consumer.accept( "> !! The current log file has rotated !!" );
                }
            }
        }


        @Override
        public void handle( String s ) {
            for ( Consumer<String> consumer : consumers ) {
                consumer.accept( s );
            }
        }


        @Override
        public void handle( Exception e ) {
            for ( Consumer<String> consumer : consumers ) {
                consumer.accept( "> !! Exception occurred: " + e.getMessage() + ". Stopping the Tailer. !!" );
            }
            this.tailer.stop();
        }
    }
}
