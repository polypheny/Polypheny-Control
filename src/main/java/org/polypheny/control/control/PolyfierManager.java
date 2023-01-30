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
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.control.httpinterface.ClientCommunicationStream;


@Slf4j
public class PolyfierManager {

    private static Thread polyfierRunnerThread;
    private static PolyfierRunner polyfierRunnerInstance;


    public static boolean start( final ClientCommunicationStream logOutputStream, final ClientCommunicationStream polyfierOutputStream ) {
        // Check status
        if ( ServiceManager.getStatus() != "idling" ) {
            polyfierOutputStream.send( "Control must be in idle state. Stop Polypheny and try again." );
            return false;
        }
        ServiceManager.polyfierMode = true;
        polyfierOutputStream.send( "Entering Polyfier mode" );

        if ( polyfierRunnerThread != null ) {
            throw new RuntimeException( "There is already a runner thread. This should not happen!" );
        }
        if ( polyfierRunnerInstance != null ) {
            throw new RuntimeException( "There is already a runner instance. This should not happen!" );
        }

        polyfierRunnerInstance = new PolyfierRunner( logOutputStream, polyfierOutputStream );
        polyfierRunnerThread = new Thread( polyfierRunnerInstance );
        polyfierRunnerThread.start();

        return true;
    }


    public static boolean stopForcefully( final ClientCommunicationStream logOutputStream, final ClientCommunicationStream polyfierOutputStream ) {
        ServiceManager.polyfierMode = false;
        polyfierOutputStream.send( "Forcefully leaving Polyfier mode" );
        // ToDo: Solve differently
        polyfierRunnerThread.stop();

        while ( polyfierRunnerThread.isAlive() ) {
            try {
                Thread.sleep( 10_000 );
            } catch ( InterruptedException e ) {
                // Ignore
            }
        }

        polyfierRunnerThread = null;
        polyfierRunnerInstance = null;

        return true;
    }


    public static boolean stopGracefully( final ClientCommunicationStream logOutputStream, final ClientCommunicationStream polyfierOutputStream ) {
        ServiceManager.polyfierMode = false;
        polyfierOutputStream.send( "Gracefully leaving Polyfier mode" );
        polyfierRunnerInstance.stopGracefully();

        while ( polyfierRunnerThread.isAlive() ) {
            try {
                Thread.sleep( 10_000 );
            } catch ( InterruptedException e ) {
                // Ignore
            }
        }

        polyfierRunnerThread = null;
        polyfierRunnerInstance = null;

        return true;
    }


    static class PolyfierRunner implements Runnable {

        private final ClientCommunicationStream logOutputStream;
        private final ClientCommunicationStream polyfierOutputStream;

        private boolean running = true;


        PolyfierRunner( final ClientCommunicationStream logOutputStream, final ClientCommunicationStream polyfierOutputStream ) {
            this.logOutputStream = logOutputStream;
            this.polyfierOutputStream = polyfierOutputStream;
        }


        @Override
        public void run() {

            //while ( running ) {
            // ToDo get job
            processJob();
            //}

        }


        void processJob() {
            polyfierOutputStream.send( "Processing Job: JOB_ID" );

            Config config = ConfigManager.getConfig();

            // Set repository
            config = config.withValue( "pcrtl.pdbms.branch", ConfigValueFactory.fromAnyRef( "master" ) );
            ConfigManager.writeConfiguration( config );

            // Build Polypheny
            ServiceManager.update( logOutputStream );

            // Starting Polypheny with --polyfier argument
            ServiceManager.start( logOutputStream, true, "--polyfier" );
        }


        public void stopGracefully() {
            running = false;
        }

    }

}
