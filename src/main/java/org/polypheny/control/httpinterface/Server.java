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

    private static final Gson gson = new Gson();


    public Server( Control control, int port ) {
        Javalin javalin = Javalin.create( config -> config.addStaticFiles( "/static" ) ).start( port );

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
    }

}
