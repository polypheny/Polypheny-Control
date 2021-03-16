/*
 * Copyright 2017-2021 The Polypheny Project
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

package org.polypheny.control.main;


import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.builder.CliBuilder;
import java.io.Console;
import java.util.HashMap;
import org.polypheny.control.authentication.AuthenticationDataManager;
import org.polypheny.control.authentication.AuthenticationFileManager;
import org.polypheny.control.authentication.AuthenticationManager;
import org.polypheny.control.control.ServiceManager;


public class Main {

    @SuppressWarnings("unchecked")
    public static void main( String[] args ) {
        // Hide dock icon on MacOS systems
        System.setProperty( "apple.awt.UIElement", "true" );

        final CliBuilder<CliRunnable> builder = Cli.builder( "polypheny-control.jar" );
        builder.withDescription( "Polypheny-DB" );

        // define top level commands
        builder.withCommand( HelpCommand.class );
        builder.withCommand( ControlCommand.class );
        builder.withCommand( TrayCommand.class );

        builder.withCommands( StartCommand.class, StopCommand.class, RestartCommand.class, UpdateCommand.class );

        // define user management commands
        builder.withCommands( AddUserCommand.class, RemoveUserCommand.class, ModifyUserCommand.class );

        builder.withDefaultCommand( TrayCommand.class );

        final CliRunnable cmd = builder.build().parse( args );

        int returnCode = cmd.run();
        System.exit( returnCode );
    }


    private static String[] getCredentials() {
        Console console = System.console();
        String name = console.readLine( "Name: " );
        String password = new String( console.readPassword( "Password: " ) );
        return new String[]{ name, password };
    }


    private static void ensureAuthenticated() {
        String[] credentials = getCredentials();
        if ( !AuthenticationManager.clientExists( credentials[0], credentials[1] ) ) {
            System.err.println( "Incorrect Credentials! Try Again!" );
            System.exit( 1 );
        }
    }


    @Command(name = "start", description = "Start Polypheny-DB")
    public static class StartCommand extends AbstractCommand {

        @Option(name = { "-t", "--tail" }, description = "Runs (java) tail on the log output")
        private final boolean tail = false;

        //
        private boolean exit = false;


        @Override
        public int _run_() {
            ensureAuthenticated();
            if ( tail ) {
                ServiceManager.start( null, true );

                Runtime.getRuntime().addShutdownHook( new Thread( () -> exit = true ) );

                while ( !exit ) {
                    Thread.yield();
                    try {
                        Thread.sleep( 1000 );
                    } catch ( InterruptedException e ) {
                        // ignore
                    }
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
            ensureAuthenticated();
            return ServiceManager.stop( null ) ? 0 : 1;
        }
    }


    @Command(name = "restart", description = "Restart Polypheny-DB")
    public static class RestartCommand extends AbstractCommand {

        @Override
        public int _run_() {
            ensureAuthenticated();
            return ServiceManager.restart( null, false ) ? 0 : 1;
        }
    }


    @Command(name = "update", description = "Update Polypheny-DB")
    public static class UpdateCommand extends AbstractCommand {

        @Override
        public int _run_() {
            ensureAuthenticated();
            return ServiceManager.update( null ) ? 0 : 1;
        }
    }


    @Command(name = "adduser", description = "Add a user")
    public static class AddUserCommand extends AbstractCommand {

        @Override
        public int _run_() {
            HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
            Console console = System.console();
            String name = console.readLine( "Name: " );
            if ( authenticationData.get( name ) != null ) {
                System.err.println( "A user with the same name exists! Try a different name!" );
                return 1;
            }
            String password = new String( console.readPassword( "Password: " ) );
            String confPassword = new String( console.readPassword( "Confirm Password: " ) );
            if ( !password.equals( confPassword ) ) {
                System.err.println( "Passwords do not match! Try Again!" );
                return 1;
            }
            AuthenticationDataManager.addAuthenticationData( name, password );
            AuthenticationFileManager.writeAuthenticationDataToFile();
            return 0;
        }
    }


    @Command(name = "remuser", description = "Remove a user")
    public static class RemoveUserCommand extends AbstractCommand {

        @Override
        public int _run_() {
            HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
            Console console = System.console();
            String name = console.readLine( "Name: " );
            if ( authenticationData.get( name ) == null ) {
                System.err.println( "User with the name \"" + name + "\" does not exist!" );
                return 1;
            }
            AuthenticationDataManager.removeAuthenticationData( name );
            AuthenticationFileManager.writeAuthenticationDataToFile();
            return 0;
        }
    }


    @Command(name = "moduser", description = "Modify a user's password")
    public static class ModifyUserCommand extends AbstractCommand {

        @Override
        public int _run_() {
            HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
            Console console = System.console();
            String name = console.readLine( "Name: " );
            if ( authenticationData.get( name ) == null ) {
                System.err.println( "User with the name \"" + name + "\" does not exist." );
                return 1;
            }
            String password = new String( console.readPassword( "Password: " ) );
            String confPassword = new String( console.readPassword( "Confirm Password: " ) );
            if ( !password.equals( confPassword ) ) {
                System.err.println( "Passwords do not match! Try Again!" );
                return 1;
            }
            AuthenticationDataManager.modifyAuthenticationData( name, password );
            AuthenticationFileManager.writeAuthenticationDataToFile();
            return 0;
        }
    }

}
