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

package ch.unibas.dmi.dbis.polyphenydb.control.main;


import ch.unibas.dmi.dbis.polyphenydb.control.control.ServiceManager;
import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.builder.CliBuilder;
import java.util.List;


public class Main {

    public static void main( String[] args ) {
        final CliBuilder<CliRunnable> builder = Cli.builder( "PolyControl" );
        builder.withDescription( "Polypheny-DB" );

        // define top level commands
        builder.withCommand( HelpCommand.class );
        builder.withCommand( ControlCommand.class );
        builder.withCommands( StartCommand.class, StopCommand.class, RestartCommand.class, UpdateCommand.class );
        builder.withGroup( "config" ).withCommands( GetConfigCommand.class, SetConfigCommand.class, DeleteConfigCommand.class );
        builder.withDefaultCommand( HelpCommand.class );

        final CliRunnable cmd = builder.build().parse( args );

        System.exit( cmd.run() );
    }


    @Command(name = "start", description = "Start Polypheny-DB")
    public static class StartCommand extends AbstractCommand {

        @Option(name = { "-t", "--tail" }, description = "Runs (java) tail on the log output")
        private boolean tail = false;

        //
        private boolean exit = false;

        @Override
        public int _run_() {
            if ( tail ) {
                ServiceManager.start( null, true );

                Runtime.getRuntime().addShutdownHook( new Thread( () -> exit = true ) );

                while ( !exit ) {
                    Thread.yield();
                }

                return 0;
            } else {
                return ServiceManager.start( null, false ) ? 0 : 1;
            }
        }
    }


    @Command(name = "stop", description = "Stop Polypheny-DB")
    public static class StopCommand extends AbstractCommand {

        @Override
        public int _run_() {
            return ServiceManager.stop( null ) ? 0 : 1;
        }
    }


    @Command(name = "restart", description = "Restart Polypheny-DB")
    public static class RestartCommand extends AbstractCommand {

        @Override
        public int _run_() {
            return ServiceManager.restart( null, false ) ? 0 : 1;
        }
    }


    @Command(name = "update", description = "Update Polypheny-DB")
    public static class UpdateCommand extends AbstractCommand {

        @Override
        public int _run_() {
            return ServiceManager.update( null ) ? 0 : 1;
        }
    }


    @Command(name = "get", description = "Gets a (web) Config value")
    public static class GetConfigCommand extends AbstractCommand {

        @Arguments(title = { "key" }, description = "Key of the configuration parameter.")
        private String key;

        @Override
        public int _run_() {
//            if ( key == null ) {
//                for ( final Enumeration propertyNames = config.propertyNames(); propertyNames.hasMoreElements(); ) {
//                    val key = propertyNames.nextElement().toString();
//                    System.out.println( key + "=" + config.getProperty( key ) );
//                }
//            } else {
//                System.out.println( key + "=" + config.getProperty( key ) );
//            }
//            return 0;
            return 1;
        }
    }


    @Command(name = "set", description = "Set a (web) Config value")
    public static class SetConfigCommand extends AbstractCommand {

        @Arguments(title = { "key=value" }, description = "Key=Value configuration pair to set.")
        private List<String> configurationEntry;

        @Override
        public int _run_() {
//            if ( configurationEntry == null ) {
//                return 1;
//            }
//
//            Properties toSet = new Properties();
//            try {
//                toSet.load( new StringReader( StringUtils.join( configurationEntry.listIterator(), '\n' ) ) );
//
//                for ( val entry : toSet.entrySet() ) {
//                    config.setProperty( entry.getKey().toString(), entry.getValue().toString() );
//                }
//
//                return ConfigManager.writeConfiguration( config ) ? 0 : 1;
//            } catch ( IOException e ) {
//                LOGGER.error( "Could not parse the given argument.", e );
//                return 2;
//            }
            return 1;
        }
    }


    @Command(name = "delete", description = "Deletes a (web) Config value")
    public static class DeleteConfigCommand extends AbstractCommand {

        @Arguments(title = { "key" }, description = "Key of the configuration parameter.")
        private String key;

        @Override
        public int _run_() {
//            if ( key == null ) {
//                return 1;
//            }
//            config.remove( key );
//            return ConfigManager.writeConfiguration( config ) ? 0 : 1;
            return 1;
        }
    }
}
