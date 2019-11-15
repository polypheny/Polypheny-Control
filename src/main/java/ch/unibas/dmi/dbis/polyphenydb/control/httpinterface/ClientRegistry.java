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


import ch.unibas.dmi.dbis.polyphenydb.control.control.ServiceManager;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class ClientRegistry {

    private static final Logger logger = LoggerFactory.getLogger( ClientRegistry.class );
    private static final Gson gson = new Gson();

    private static final Map<Session, Integer> clientMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Session> reverseClientMap = new ConcurrentHashMap<>();
    private static int nextClientNumber = 1;


    static void broadcast( String topic, String message ) {
        clientMap.keySet().stream().filter( Session::isOpen ).forEach( session -> sendMessage( session, topic, message ) );
    }


    static void sendMessage( int clientId, String topic, String message ) {
        Session session = reverseClientMap.get( clientId );
        sendMessage( session, topic, message );
    }


    private static void sendMessage( Session session, String topic, String message ) {
        int clientId = clientMap.get( session );
        Map<String, String> map = new HashMap<>();
        map.put( topic, message );
        try {
            logger.debug( "Send message to client " + clientId + ": topic: " + topic + " | message: " + message );
            session.getRemote().sendString( String.valueOf( gson.toJson( map ) ) );
        } catch ( Exception e ) {
            logger.debug( "Exception thrown while sending message to client " + clientId, e );
        }
    }


    static synchronized void addClient( Session session ) {
        int cid = nextClientNumber++;
        clientMap.put( session, cid );
        reverseClientMap.put( cid, session );
        sendMessage( cid, "clientId", "" + cid );
        logger.info( "Registered client " + cid + " from IP " + session.getRemoteAddress().getAddress().getHostAddress() );
        sendMessage( cid, "status", "" + ServiceManager.getStatus() );
    }


    static void removeClient( Session session, int statusCode, String reason ) {
        int cid = clientMap.remove( session );
        reverseClientMap.remove( cid );
        logger.info( "Removed client " + cid + " from IP " + session.getRemoteAddress().getAddress().getHostAddress() );
    }

}
