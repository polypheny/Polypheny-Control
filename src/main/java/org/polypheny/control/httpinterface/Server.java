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

package org.polypheny.control.httpinterface;


import com.google.gson.Gson;
import io.javalin.Javalin;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.control.control.Control;
import org.polypheny.control.control.ServiceManager;


@Slf4j
public class Server {

    private static final Gson GSON = new Gson();


    public Server( Control control, int port ) {
        Javalin javalin = Javalin.create( config -> config.staticFiles.add( "/static" ) ).start( port );

        javalin.ws( "/socket/", ws -> {
            ws.onConnect( ClientRegistry::addClient );
            ws.onClose( ClientRegistry::removeClient );
        } );

        javalin.before( ctx -> {
            log.debug( "Received api call: {}", ctx.path() );
        } );

        // /config
        javalin.get( "/config/get", control::getCurrentConfigAsJson );
        javalin.post( "/config/set", control::setConfig );

        // /control
        javalin.post( "/control/start", control::start );
        javalin.post( "/control/stop", control::stop );
        javalin.post( "/control/restart", control::restart );
        javalin.post( "/control/update", control::update );
        javalin.get( "/control/version", control::getVersion );
        javalin.get( "/control/controlVersion", control::getControlVersion );
        javalin.get( "/control/status", control::getStatus );
        javalin.get( "/control/pdbBranches", control::getAvailablePdbBranches );
        javalin.get( "/control/puiBranches", control::getAvailablePuiBranches );

        // /polyfier
        javalin.post( "/polyfier/start", control::polyfierStart );
        javalin.post( "/polyfier/stopForcefully", control::polyfierStopForcefully );
        javalin.post( "/polyfier/stopGracefully", control::polyfierStopGracefully );

        // Client
        javalin.post( "/client/type", ClientRegistry::setClientType );

        // Periodically sent status to all clients to keep the connection open
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(
                () -> ClientRegistry.broadcast( "status", "" + ServiceManager.getStatus() ),
                0,
                2,
                TimeUnit.SECONDS );

        // For switching background color when a benchmarking client is connected
        exec.scheduleAtFixedRate(
                () -> ClientRegistry.broadcast( "benchmarkerConnected", "" + ClientRegistry.getBenchmarkerConnected() ),
                0,
                5,
                TimeUnit.SECONDS );

        // Periodically sent versions to clients
        exec.scheduleAtFixedRate(
                () -> ClientRegistry.broadcast( "version", ServiceManager.getVersion() ),
                0,
                20,
                TimeUnit.SECONDS );

        log.info( "Polypheny Control is running on port {}", port );
    }

}
