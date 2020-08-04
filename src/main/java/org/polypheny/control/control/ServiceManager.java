/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import lombok.val;
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
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;
import org.polypheny.control.httpinterface.ClientCommunicationStream;
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


    /**
     * This block restores the PolyphenyDbProcess on startup by checking the PID file. It will create a PolyphenyDbProcess
     * from the PID if the file contains a PID number. Then it will check if the process is still alive.
     * <p>
     * TODO: This is maybe not required since we start a child process. By the termination of this process usually the child
     * processes are terminated too. However, if later on there is another way of creating the polypheny-db process this
     * static block would be more relevant (I guess).
     */
    private static void restorePolyphenyDbProcess() {
        val configuration = ConfigManager.getConfig();
        val workingDir = new File( configuration.getString( "pcrtl.workingdir" ) );
        if ( workingDir.exists() == false ) {
            if ( workingDir.mkdirs() == false ) {
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

        val pidFile = new File( configuration.getString( "pcrtl.pdbms.pidfile" ) );
        try {
            FileUtils.touch( pidFile );
            if ( pidFile.exists() ) {
                // should always be true...

                try ( val pidReader = new BufferedReader( new InputStreamReader( new FileInputStream( pidFile ), StandardCharsets.UTF_8 ) ) ) {
                    val line = pidReader.readLine();
                    if ( line != null && line.isEmpty() == false ) {
                        // Restore
                        polyphenyDbProcess = PolyphenyDbProcess.createFromPid( Integer.parseInt( line ) );
                    }
                } catch ( FileNotFoundException e ) {
                    log.error( "File exists but not found?!", e );
                } catch ( IOException e ) {
                    log.error( "IOException while recovering the PID.", e );
                }

                if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() == false ) {
                    // if dead, make sure the file is empty
                    try ( val pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile ), StandardCharsets.UTF_8 ) ) {
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
        val configuration = ConfigManager.getConfig();
        synchronized ( MUTEX ) {
            //restorePolyphenyDbProcess();

            if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Polypheny-DB is already running. Stop it first or use the restart function." );
                }
                log.warn( "> Polypheny-DB is already running. Stop it first or use the restart function." );
                return false;
            }

            val workingDir = configuration.getString( "pcrtl.workingdir" );
            val pidFile = configuration.getString( "pcrtl.pdbms.pidfile" );
            val logsDir = configuration.getString( "pcrtl.logsdir" );
            val javaExecutable = configuration.getString( "pcrtl.java.executable" ) + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
            val javaOptions = configuration.getStringList( "pcrtl.java.options" );
            val javaMaximumHeapSize = configuration.getString( "pcrtl.java.heap" );
            val pdbmsJar = configuration.getString( "pcrtl.pdbms.jarfile" );
            val pdbmsMainClass = configuration.getString( "pcrtl.pdbms.mainclass" );
            val pdbmsArgs = configuration.getString( "pcrtl.pdbms.args" );
            val logFile = new File( new File( logsDir ), new SimpleDateFormat( "'polypheny-db_'yyyy.MM.dd_HH-mm-ss.SSS'.log'" ).format( new Date() ) ).getAbsolutePath();
            val errFile = logFile.substring( 0, logFile.lastIndexOf( '.' ) ) + ".err.log";
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

            if ( new File( javaExecutable ).exists() == false ) {
                throw new RuntimeException( "The java executable seems not to exist... How did you start this application?!" );
            }

            if ( new File( pdbmsJar ).exists() == false ) {
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> There is no Polypheny-DB jar file. Trigger an update first." );
                }
                log.warn( "> There is no Polypheny-DB jar file. Trigger an update first." );
                return false;
            }

            if ( new File( logsDir ).exists() == false ) {
                if ( new File( logsDir ).mkdirs() == false ) {
                    throw new RuntimeException( "Could not create the logs directory." );
                }
            }

            try ( val pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile, false ), StandardCharsets.UTF_8 ) ) {

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

                val polyphenyDbProcessId = polyphenyDbProcess.getPid();
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
        val configuration = ConfigManager.getConfig();
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
            val pidFile = new File( configuration.getString( "pcrtl.pdbms.pidfile" ) );
            //

            polyphenyDbProcess = null;

            // stopping std out redirectors
            if ( logTailer != null ) {
                logTailer.stop();
            }
            logTailer = null;
            if ( errTailer != null ) {
                errTailer.stop();
            }
            errTailer = null;

            if ( pidFile.delete() == false ) {
                try ( val pidWriter = new OutputStreamWriter( new FileOutputStream( pidFile, false ), StandardCharsets.UTF_8 ) ) {
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
        val configuration = ConfigManager.getConfig();
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
                    return false;
                }

                val workingDir = configuration.getString( "pcrtl.workingdir" );
                val builddir = configuration.getString( "pcrtl.builddir" );

                if ( !new File( workingDir ).exists() ) {
                    if ( !new File( workingDir ).mkdirs() ) {
                        throw new RuntimeException( "Could not create the folders for " + new File( workingDir ).getAbsolutePath() );
                    }
                }

                val cleanMode = configuration.getString( "pcrtl.clean.mode" );
                boolean clean = false;
                if ( cleanMode.equals( "always" ) ) {
                    clean = true;
                } else if ( cleanMode.equals( "branchChange" ) ) {
                    Map<String, String> versions = getVersion();
                    if ( !versions.get( "pdb-branch" ).equals( configuration.getString( "pcrtl.pdbms.branch" ) ) ) {
                        clean = true;
                    }
                    if ( !versions.get( "pui-branch" ).equals( configuration.getString( "pcrtl.ui.branch" ) ) ) {
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

                val buildMode = configuration.getString( "pcrtl.buildmode" );
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
                }
                return true;
            } finally {
                currentlyUpdating = false;
            }
        }
    }


    private static void buildPdb( final ClientCommunicationStream clientCommunicationStream, Config configuration, boolean forceInstall ) {
        boolean requiresBuild = false;

        val pdbBuildDir = new File( configuration.getString( "pcrtl.pdbbuilddir" ) );
        val repo = configuration.getString( "pcrtl.pdbms.repo" );
        val branch = configuration.getString( "pcrtl.pdbms.branch" );

        // Delete old DBMS Jar
        val oldJar = new File( configuration.getString( "pcrtl.pdbms.oldjarfile" ) );
        if ( oldJar.exists() ) {
            if ( !oldJar.delete() ) {
                log.info( "> Unable to delete the old jar file." );
                if ( clientCommunicationStream != null ) {
                    clientCommunicationStream.send( "> Unable to delete the old jar file." );
                }
            }
        }

        // Rename DBMS Jar
        val jar = new File( configuration.getString( "pcrtl.pdbms.jarfile" ) );
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
            val oldCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
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
            val newCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            requiresBuild |= !oldCommitId.equals( newCommitId );
            git.close();
        } catch ( GitAPIException | IOException e ) {
            throw new RuntimeException( e );
        }
        log.info( "> Pulling Polypheny-DB repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-DB repository ... finished." );
        }

        // Check if we need to build
        if ( !requiresBuild && !forceInstall ) {
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

        // Build
        log.info( "> Building Polypheny-DB ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Building Polypheny-DB ..." );
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( pdbBuildDir ).connect() ) {
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( System.out )
                    .forTasks( "build" )
                    .withArguments( "-x", "test" );

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
        val dbmsJarFolder = new File( pdbBuildDir, "dbms" + File.separator + "build" + File.separator + "libs" );
        File[] files = dbmsJarFolder.listFiles( ( dir, name ) -> name.startsWith( "dbms-" ) );

        File dbmsJar = null;
        if ( files != null ) {
            for ( File f : files ) {
                if ( !f.getName().contains( "javadoc" ) && !f.getName().contains( "sources" ) ) {
                    dbmsJar = f;
                    break;
                }
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
    }


    public static void clonePdbRepository( ClientCommunicationStream clientCommunicationStream, Config configuration ) {
        val pdbBuildDir = new File( configuration.getString( "pcrtl.pdbbuilddir" ) );
        val repo = configuration.getString( "pcrtl.pdbms.repo" );
        val branch = configuration.getString( "pcrtl.pdbms.branch" );

        log.info( "> Cloning Polypheny-DB repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-DB repository ..." );
        }
        try {
            final Git git = Git.cloneRepository()
                    .setURI( repo )
                    .setDirectory( pdbBuildDir )
                    .setBranch( branch )
                    .call();
            git.close();
        } catch ( GitAPIException e ) {
            throw new RuntimeException( e );
        }
        log.info( "> Cloning Polypheny-DB repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-DB repository ... finished." );
        }
    }


    // return value indicated whether the repo has been installed (false if there haven't been any changes)
    private static boolean installUi( final ClientCommunicationStream clientCommunicationStream, Config configuration ) {
        boolean requiresInstall = false;

        val buildDir = configuration.getString( "pcrtl.builddir" );
        val repo = configuration.getString( "pcrtl.ui.repo" );
        val branch = configuration.getString( "pcrtl.ui.branch" );

        val uiBuildDir = new File( buildDir, "ui" );

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
            val oldCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
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
            val newCommitId = git.getRepository().resolve( Constants.HEAD ).getName();
            requiresInstall |= !oldCommitId.equals( newCommitId );
            git.close();
        } catch ( GitAPIException | IOException e ) {
            throw new RuntimeException( e );
        }
        log.info( "> Pulling Polypheny-UI repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Pulling Polypheny-UI repository ... finished." );
        }

        // Check if we need to build
        if ( !requiresInstall ) {
            log.info( "> No changes to UI repository and therefore no need to rebuild Polypheny-UI ..." );
            if ( clientCommunicationStream != null ) {
                clientCommunicationStream.send( "> No changes to UI repository and therefore no need to rebuild Polypheny-UI ..." );
            }
            return false;
        }

        // Build
        log.info( "> Installing Polypheny-UI ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Installing Polypheny-UI ..." );
        }
        try ( ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory( uiBuildDir ).connect() ) {
            BuildLauncher buildLauncher = connection.newBuild()
                    .setStandardOutput( System.out )
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
        val buildDir = configuration.getString( "pcrtl.builddir" );
        val repo = configuration.getString( "pcrtl.ui.repo" );
        val branch = configuration.getString( "pcrtl.ui.branch" );
        val uiBuildDir = new File( buildDir, "ui" );

        log.info( "> Cloning Polypheny-UI repository ..." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-UI repository ..." );
        }
        try {
            final Git git = Git.cloneRepository()
                    .setURI( repo )
                    .setDirectory( uiBuildDir )
                    .setBranch( branch )
                    .call();
            git.close();
        } catch ( GitAPIException e ) {
            throw new RuntimeException( e );
        }
        log.info( "> Cloning Polypheny-UI repository ... finished." );
        if ( clientCommunicationStream != null ) {
            clientCommunicationStream.send( "> Cloning Polypheny-UI repository ... finished." );
        }
    }


    public static Map<String, String> getVersion() {
        val configuration = ConfigManager.getConfig();
        val buildDir = configuration.getString( "pcrtl.builddir" );
        val pdbBuildDir = new File( buildDir, "pdb" );
        val puiBuildDir = new File( buildDir, "ui" );

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
            log.error( "Error while retrieving pdb version", e );
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
            log.error( "Error while retrieving pui version", e );
        }

        return map;
    }


    public static Object getStatus() {
        if ( polyphenyDbProcess != null && polyphenyDbProcess.isAlive() ) {
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


    private static boolean validateGitRepository( Repository repo ) {
        try {
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
                for ( val consumer : consumers ) {
                    consumer.accept( "> !! The error log file was not found. Stopping the Tailer. !!" );
                }
            } else {
                for ( val consumer : consumers ) {
                    consumer.accept( "> !! The log file was not found. Stopping the Tailer. !!" );
                }
            }
            this.tailer.stop();
        }


        @Override
        public void fileRotated() {
            if ( this.tailer.getFile().getName().endsWith( ".err.log" ) ) {
                for ( val consumer : consumers ) {
                    consumer.accept( "> !! The current error log file has rotated !!" );
                }
            } else {
                for ( val consumer : consumers ) {
                    consumer.accept( "> !! The current log file has rotated !!" );
                }
            }
        }


        @Override
        public void handle( String s ) {
            for ( val consumer : consumers ) {
                consumer.accept( s );
            }
        }


        @Override
        public void handle( Exception e ) {
            for ( val consumer : consumers ) {
                consumer.accept( "> !! Exception occurred: " + e.getLocalizedMessage() + ". Stopping the Tailer. !!" );
            }
            this.tailer.stop();
        }
    }
}
