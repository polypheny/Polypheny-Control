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

package ch.unibas.dmi.dbis.polyphenydb.control.httpinterface;


import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFileLocation;
import static spark.Spark.webSocket;

import ch.unibas.dmi.dbis.polyphenydb.control.control.Control;
import ch.unibas.dmi.dbis.polyphenydb.control.control.ServiceManager;
import com.google.gson.Gson;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Server {

    private static final Gson gson = new Gson();


    public Server( Control control, int port ) {
        port( port );

        staticFileLocation( "/static" );
        webSocket( "/socket", WebSocket.class );

        path( "/", () -> {
            before( ( q, a ) -> log.info( "Received api call" ) );
            path( "/config", () -> {
                get( "/get", control::getCurrentConfigAsJson );
                post( "/set", control::setConfig, gson::toJson );
            } );
            path( "/control", () -> {
                post( "/start", control::start, gson::toJson );
                post( "/stop", control::stop, gson::toJson );
                post( "/restart", control::restart, gson::toJson );
                post( "/update", control::update, gson::toJson );
                get( "/version", control::getVersion );
                get( "/status", control::getStatus, gson::toJson );
            } );
        } );

        // Periodically sent status to all clients to keep the connection open
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate( () -> ClientRegistry.broadcast( "status", "" + ServiceManager.getStatus() ), 0, 5, TimeUnit.SECONDS );
    }

}
