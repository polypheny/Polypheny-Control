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

package ch.unibas.dmi.dbis.polyphenydb.control.control;


import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PolyphenyDbProcessBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger( PolyphenyDbProcessBuilder.class );

    private File workingDir;
    private File javaExecutable;
    private String[] javaOptions;
    private File pdbmsJarFile;
    private String classPath;
    private String mainClass;
    private String[] arguments;
    private File logFile;
    private boolean logFileAppend;
    private File errFile;
    private boolean errFileAppend;


    private PolyphenyDbProcessBuilder() {
    }


    static PolyphenyDbProcessBuilder builder() {
        return new PolyphenyDbProcessBuilder();
    }


    PolyphenyDbProcessBuilder withWorkingDir( final File workingDir ) {
        this.workingDir = workingDir;
        return this;
    }


    PolyphenyDbProcessBuilder withJavaExecutable( final File javaExecutable ) {
        this.javaExecutable = javaExecutable;
        return this;
    }


    PolyphenyDbProcessBuilder withJavaOptions( final String... javaOptions ) {
        this.javaOptions = javaOptions;
        return this;
    }


    PolyphenyDbProcessBuilder withPolyphenyDbmsJar( final File polyphenyDbmsJar ) {
        this.pdbmsJarFile = polyphenyDbmsJar;
        return this;
    }


    PolyphenyDbProcessBuilder withClassPath( final String... classPath ) {
        this.classPath = String.join( File.pathSeparator, classPath );
        return this;
    }


    PolyphenyDbProcessBuilder withMainClass( final String mainClass ) {
        this.mainClass = mainClass;
        return this;
    }


    PolyphenyDbProcessBuilder withArguments( final String... arguments ) {
        this.arguments = arguments;
        return this;
    }


    PolyphenyDbProcessBuilder withLogFile( final File logFile, final boolean append ) {
        this.logFile = logFile;
        this.logFileAppend = append;
        return this;
    }


    PolyphenyDbProcessBuilder withErrFile( final File errFile, final boolean append ) {
        this.errFile = errFile;
        this.errFileAppend = append;
        return this;
    }


    PolyphenyDbProcess start() throws IOException {

        if ( javaExecutable.exists() == false ) {
            throw new RuntimeException( "The java executable seems not to exist... How did you start this application?!" );
        }

        if ( workingDir.exists() == false ) {
            if ( workingDir.mkdirs() == false ) {
                throw new RuntimeException( "Could not create the folders for " + workingDir.getAbsolutePath() );
            }
        }

        final LinkedList<String> command = new LinkedList<>();

        command.addFirst( javaExecutable.getAbsolutePath() );
        command.addAll( Arrays.asList( javaOptions ) );
        if ( pdbmsJarFile != null && pdbmsJarFile.exists() ) {
            command.add( "-jar" );
            command.add( pdbmsJarFile.getAbsolutePath() );
        } else {
            command.add( "-classpath" );
            // The following OS specific stuff is maybe not needed. However, we should check spaces in the classpath which may require the following code...:
            /*if ( SystemUtils.IS_OS_WINDOWS ) {
                command.add( "\"" + classPath + "\"" );
            } else {
                command.add( classPath.replaceAll( " ", "\\ " ) );
            }*/
            command.add( classPath );
            command.add( mainClass );
        }
        command.addAll( Arrays.asList( arguments ) );

        LOGGER.info( "> Executing \"{}\"", String.join( " ", command ) );

        ProcessBuilder builder = new ProcessBuilder();
        if ( logFile == null ) {
            builder.redirectOutput( Redirect.PIPE );
        } else if ( logFileAppend ) {
            builder.redirectOutput( Redirect.appendTo( logFile ) );
        } else {
            builder.redirectOutput( Redirect.to( logFile ) );
        }
        if ( errFile == null ) {
            builder.redirectError( Redirect.PIPE );
        } else if ( errFileAppend ) {
            builder.redirectError( Redirect.appendTo( errFile ) );
        } else {
            builder.redirectError( Redirect.to( errFile ) );
        }
        builder.command( command );
        builder.directory( workingDir );

        return PolyphenyDbProcess.createFromProcess( builder.start() );
    }
}
