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
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.apache.commons.io.FileUtils;
import org.polypheny.control.authentication.AuthenticationContext;
import org.polypheny.control.authentication.AuthenticationDataManager;
import org.polypheny.control.authentication.AuthenticationFileManager;
import org.polypheny.control.authentication.AuthenticationManager;
import org.polypheny.control.authentication.AuthenticationUtils;
import org.polypheny.control.control.ServiceManager;


public class Main {

    @SuppressWarnings("unchecked")
    public static void main( String[] args ) {
        // Check for and restore .polypheny.backup
        if ( System.getProperty( "testing" ) != null && System.getProperty( "testing" ).equals( "true" ) ) {
            restorePolyphenyBackup();
        }

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


    private static void restorePolyphenyBackup() {
        File polyphenyDir = new File( System.getProperty( "user.home" ), ".polypheny" );
        File polyphenyDirBackup = new File( System.getProperty( "user.home" ), ".polypheny.backup" );

        try {
            if ( polyphenyDirBackup.exists() ) {
                FileUtils.deleteDirectory( polyphenyDir );
                polyphenyDirBackup.renameTo( polyphenyDir );
            }
        } catch ( IOException ex ) {
            // Could not delete polyphenyDir
            System.err.println( "Error restoring backup of polypheny directory: " + polyphenyDirBackup.getAbsolutePath() + "." );
            System.err.println( "Please restore the backup manually by renaming the folder to '.polypheny'." );
            System.exit( 1 );
        }
    }


    private static String[] getCredentials() {
        Console console = System.console();
        String name = console.readLine( "Name: " );
        String password = new String( console.readPassword( "Password: " ) );
        return new String[]{ name, password };
    }


    private static void ensureAuthenticated() {
        if ( AuthenticationUtils.shouldAuthenticate( AuthenticationContext.CLI ) ) {
            String[] credentials = getCredentials();
            if ( !AuthenticationManager.clientExists( credentials[0], credentials[1] ) ) {
                System.err.println( "Incorrect Credentials! Try Again!" );
                System.exit( 1 );
            }
        }
    }


    private static void authorizeAdmin() {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        String adminPassword = authenticationData.get( "admin" );

        if ( adminPassword == null ) {
            System.err.println( "'admin' user does not exist! Please create a admin user before proceeding." );
            System.exit( 1 );
        }

        Console console = System.console();

        for ( int i = 1; i <= 3; i++ ) {
            String password = new String( console.readPassword( "Enter 'admin' password (Try %d/3): ", i ) );
            if ( !AuthenticationManager.clientExists( "admin", password ) ) {
                System.err.println( "Incorrect Credentials!" );
            } else {
                return;
            }
        }
        System.err.println( "3 incorrect password attempts." );
        System.exit( 1 );
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

            if ( password.isEmpty() ) {
                System.err.println( "Password cannot be empty." );
                System.exit( 1 );
            }

            String confPassword = new String( console.readPassword( "Confirm Password: " ) );
            if ( !password.equals( confPassword ) ) {
                System.err.println( "Passwords do not match! Try Again!" );
                return 1;
            }

            if ( !name.equals( "admin" ) ) {
                authorizeAdmin();
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

            if ( !name.equals( "admin" ) ) {
                authorizeAdmin();
            } else {
                System.err.println( "Cannot remove user 'admin'." );
                System.exit( 1 );
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

            if ( password.isEmpty() ) {
                System.err.println( "Password cannot be empty." );
                System.exit( 1 );
            }

            String confPassword = new String( console.readPassword( "Confirm Password: " ) );
            if ( !password.equals( confPassword ) ) {
                System.err.println( "Passwords do not match! Try Again!" );
                return 1;
            }

            authorizeAdmin();

            AuthenticationDataManager.modifyAuthenticationData( name, password );
            AuthenticationFileManager.writeAuthenticationDataToFile();
            return 0;
        }

    }

}
