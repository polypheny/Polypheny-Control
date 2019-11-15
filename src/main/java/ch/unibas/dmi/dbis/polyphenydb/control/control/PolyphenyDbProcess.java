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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.SystemUtils;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;


@Slf4j
public abstract class PolyphenyDbProcess {


    public static PolyphenyDbProcess createFromPid( final int pid ) {
        if ( SystemUtils.IS_OS_WINDOWS ) {
            return new WindowsPolyphenyDbProcess( pid );
        } else if ( SystemUtils.IS_OS_UNIX ) {
            return new UnixPolyphenyDbProcess( pid );
        } else {
            throw new UnsupportedOperationException( "This Operating System is currently not supported." );
        }
    }


    static PolyphenyDbProcess createFromProcess( final Process process ) {
        if ( SystemUtils.IS_OS_WINDOWS ) {
            return new WindowsPolyphenyDbProcess( process );
        } else if ( SystemUtils.IS_OS_UNIX ) {
            return new UnixPolyphenyDbProcess( process );
        } else {
            throw new UnsupportedOperationException( "This Operating System is currently not supported." );
        }
    }


    protected final Process process;


    protected PolyphenyDbProcess( final Process process ) {
        this.process = process;
    }


    public abstract int getPid();

    public abstract boolean isAlive();

    public abstract void kill();

    public abstract void killForcibly();


    public final InputStream getProcessOutput() {
        if ( process == null ) {
            return null;
        } else {
            return process.getInputStream();
        }
    }


    public final InputStream getProcessError() {
        if ( process == null ) {
            return null;
        } else {
            return process.getErrorStream();
        }
    }


    public final OutputStream getProcessInput() {
        if ( process == null ) {
            return null;
        } else {
            return process.getOutputStream();
        }
    }


    /**
     *
     */
    private static final class WindowsPolyphenyDbProcess extends PolyphenyDbProcess {

        private final WinProcess winProcess;


        protected WindowsPolyphenyDbProcess( final int pid ) {
            super( null );
            winProcess = new WinProcess( pid );
        }


        protected WindowsPolyphenyDbProcess( final Process process ) {
            super( process );
            winProcess = new WinProcess( process );
        }


        @Override
        public int getPid() {
            return winProcess.getPid();
        }


        @Override
        public boolean isAlive() {
            if ( super.process == null ) {
                // "PID Mode"
                try {
                    winProcess.getEnvironmentVariables();
                    return true;
                } catch ( WinpException ex ) {
                    return false;
                }
            } else {
                return super.process.isAlive();
            }
        }


        @Override
        public void kill() {
            // "PID Mode"
            try {
                val kill = Runtime.getRuntime().exec( "TASKKILL /PID " + getPid() ); // SIGTERM -- CTRL + C
                if ( kill.waitFor( 1, TimeUnit.MINUTES ) ) {
                    // terminated within the time
                    if ( kill.exitValue() == 0 ) {
                        return;
                    }
                }

                killForcibly();

            } catch ( IOException e ) {
                log.error( "Exception while trying to kill <<me:" + getPid() + ">> via PID on Unix.", e );
            } catch ( InterruptedException e ) {
                log.warn( "Interrupted while waiting for kill to finish." );
            }
        }


        @Override
        public void killForcibly() {
            if ( super.process == null ) {
                // "PID Mode"
                winProcess.kill();
            } else {
                super.process.destroyForcibly();
            }
        }
    }


    /**
     *
     */
    private static final class UnixPolyphenyDbProcess extends PolyphenyDbProcess {

        private final int pid;


        protected UnixPolyphenyDbProcess( final int pid ) {
            super( null );
            this.pid = pid;
        }


        protected UnixPolyphenyDbProcess( final Process process ) {
            super( process );

            long pid = -1;

            try {
                val pidField = process.getClass().getDeclaredField( "pid" );

                AccessController.doPrivileged( (PrivilegedAction<Void>) () -> {
                    pidField.setAccessible( true );
                    return null;
                } );

                pid = pidField.getLong( process );

                AccessController.doPrivileged( (PrivilegedAction<Void>) () -> {
                    pidField.setAccessible( false );
                    return null;
                } );

            } catch ( NoSuchFieldException | IllegalAccessException e ) {
                log.error( "Exception while extracting the PID from java.lang.Process.", e );
                pid = -1;
            } finally {
                this.pid = (int) pid;
            }
        }


        @Override
        public int getPid() {
            return pid;
        }


        @Override
        public boolean isAlive() {
            if ( super.process == null ) {
                // "PID Mode"
                try {
                    val process = Runtime.getRuntime().exec( "ps -p " + pid );

                    try ( val input = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ) {
                        String line;
                        while ( (line = input.readLine()) != null ) {
                            if ( line.contains( " " + pid + " " ) ) {
                                return true;
                            }
                        }
                    }

                    return false;
                } catch ( IOException e ) {
                    log.warn( "IOException while checking if " + pid + " is still alive.", e );
                    return false;
                }
            } else {
                return super.process.isAlive();
            }
        }


        @Override
        public void kill() {
            // "PID Mode"
            try {
                val kill = Runtime.getRuntime().exec( "kill -15 " + pid ); // SIGTERM -- CTRL + C
                if ( kill.waitFor( 1, TimeUnit.MINUTES ) ) {
                    // terminated within the time
                    if ( kill.exitValue() == 0 ) {
                        return;
                    }
                }

                killForcibly();

            } catch ( IOException e ) {
                log.error( "Exception while trying to kill <<me:" + pid + ">> via PID on Unix.", e );
            } catch ( InterruptedException e ) {
                log.warn( "Interrupted while waiting for kill to finish." );
            }
        }


        @Override
        public void killForcibly() {
            if ( super.process == null ) {
                // "PID Mode"
                try {
                    val kill = Runtime.getRuntime().exec( "kill -9 " + pid ); // SIGKILL

                    if ( kill.waitFor( 1, TimeUnit.SECONDS ) ) {
                        // terminated within the time
                        if ( kill.exitValue() == 0 ) {
                            return;
                        }
                    }

                    log.warn( "Could not kill {}", pid );

                } catch ( IOException e ) {
                    log.error( "Exception while trying to kill <<me:" + pid + ">> via PID on Unix.", e );
                } catch ( InterruptedException e ) {
                    log.warn( "Interrupted while waiting for kill to finish." );
                }

            } else {
                super.process.destroyForcibly();
            }
        }
    }
}
