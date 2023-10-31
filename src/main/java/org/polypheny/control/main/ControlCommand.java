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


import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.typesafe.config.Config;
import java.util.HashMap;
import org.polypheny.control.authentication.AuthenticationFileManager;
import org.polypheny.control.control.ConfigManager;
import org.polypheny.control.control.Control;
import org.polypheny.control.httpinterface.Server;


@Command(name = "control", description = "Start Polypheny Control")
public class ControlCommand extends AbstractCommand {

    @Option(name = { "-p", "--port" }, description = "Overwrite port of the Polypheny Control dashboard and API.")
    private final int port = -1;

    @Option(name = { "-x", "--suppress-warning" }, description = "Suppress the auth warnings on startup.")
    protected boolean suppressWarning = false;

    private volatile Boolean running = true;


    @Override
    public int _run_() {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        Config config = ConfigManager.getConfig();
        if ( config.getBoolean( "pcrtl.auth.enable" ) ) {
            if ( !suppressWarning && authenticationData.isEmpty() ) {
                warnNoUserAccounts();
            }
            if ( !suppressWarning && !config.getBoolean( "pcrtl.auth.local" ) ) {
                warnNoAuthOnLocalhost();
            }
        } else {
            if ( !suppressWarning ) {
                warnAuthDisabled();
            }
        }
        Control control = new Control();
        final Server server;
        if ( port > 0 ) {
            server = new Server( control, port );
        } else {
            server = new Server( control, ConfigManager.getConfig().getInt( "pcrtl.control.port" ) );
        }

        Runtime.getRuntime().addShutdownHook( new Thread( () -> running = false ) );

        while ( running ) {
            Thread.yield();
            try {
                Thread.sleep( 1000 );
            } catch ( InterruptedException e ) {
                // ignore
            }
        }

        server.shutdown();

        return 0;
    }


    private static void warnNoUserAccounts() {
        System.out.println( "WARNING: No Users Exist. Polypheny-Control executes and manages Polypheny-DB." );
        System.out.println( "WARNING: For security reasons it is advisable to create at least one user." );
        System.out.println( "WARNING: To learn more about User Management and Authentication, visit " );
        System.out.println( "WARNING: https://docs.polypheny.com/en/latest/devs/polypheny-control#authentication\n\n" );
    }


    private static void warnNoAuthOnLocalhost() {
        System.out.println( "WARNING: Authentication for requests from localhost are disabled." );
        System.out.println( "WARNING: To learn more about User Management and Authentication, visit " );
        System.out.println( "WARNING: https://docs.polypheny.com/en/latest/devs/polypheny-control#authentication\n\n" );
    }


    private static void warnAuthDisabled() {
        System.out.println( "WARNING: Authentication is disabled." );
        System.out.println( "WARNING: To learn more about User Management and Authentication, visit " );
        System.out.println( "WARNING: https://docs.polypheny.com/en/latest/devs/polypheny-control#authentication\n\n" );
    }


    public int runWithControlledShutdown( Boolean running ) {
        this.running = running;
        return _run_();
    }

}
