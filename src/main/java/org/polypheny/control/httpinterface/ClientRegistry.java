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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.polypheny.control.control.ServiceManager;
import spark.Request;
import spark.Response;


@Slf4j
class ClientRegistry {

    private static final Gson gson = new Gson();

    private static final Map<Session, Client> clientMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Client> reverseClientMap = new ConcurrentHashMap<>();
    private static int nextClientNumber = 1;


    static void broadcast( String topic, String message ) {
        clientMap.keySet().stream().filter( Session::isOpen ).forEach( session -> sendMessage( session, topic, message ) );
    }


    static void broadcast( String topic, Map<String, String> map ) {
        clientMap.keySet().stream().filter( Session::isOpen ).forEach( session -> sendMessage( session, topic, map ) );
    }


    static void sendMessage( int clientId, String topic, String message ) {
        Session session = reverseClientMap.get( clientId ).getSession();
        sendMessage( session, topic, message );
    }


    static void sendMessage( int clientId, String topic, Map<String, String> msgMap ) {
        Session session = reverseClientMap.get( clientId ).getSession();
        sendMessage( session, topic, msgMap );
    }


    private static void sendMessage( Session session, String topic, String message ) {
        Client client = clientMap.get( session );
        Map<String, String> map = new HashMap<>();
        map.put( topic, message );
        try {
            log.debug( "Send message to client " + client.getClientId() + ": topic: " + topic + " | message: " + message );
            session.getRemote().sendString( String.valueOf( gson.toJson( map ) ) );
        } catch ( Exception e ) {
            log.debug( "Exception thrown while sending message to client " + client.getClientId(), e );
        }
    }


    private static void sendMessage( Session session, String topic, Map<String, String> msgMap ) {
        Client client = clientMap.get( session );
        Map<String, Map<String, String>> map = new HashMap<>();
        map.put( topic, msgMap );
        try {
            log.debug( "Send message to client " + client.getClientId() + ": topic: " + topic + " | message: (MAP)" );
            session.getRemote().sendString( String.valueOf( gson.toJson( map ) ) );
        } catch ( Exception e ) {
            log.debug( "Exception thrown while sending message to client " + client.getClientId(), e );
        }
    }


    static synchronized void addClient( Session session ) {
        int cid = nextClientNumber++;
        Client client = new Client( session, cid );
        clientMap.put( session, client );
        reverseClientMap.put( cid, client );
        sendMessage( cid, "clientId", "" + cid );
        log.info( "Registered client " + cid + " from IP " + session.getRemoteAddress().getAddress().getHostAddress() );
        sendMessage( cid, "status", "" + ServiceManager.getStatus() );
        sendMessage( cid, "benchmarkerConnected", "" + ClientRegistry.getBenchmarkerConnected() );
        sendMessage( cid, "version", ServiceManager.getVersion() );
    }


    static void removeClient( Session session, int statusCode, String reason ) {
        Client client = clientMap.remove( session );
        reverseClientMap.remove( client.clientId );
        log.info( "Removed client " + client.clientId + " from IP " + session.getRemoteAddress().getAddress().getHostAddress() );
    }


    public static Object setClientType( Request request, Response response ) {
        if ( request.queryParams().contains( "clientType" ) && request.queryParams().contains( "clientId" ) ) {
            String type = request.queryParams( "clientType" );
            int cid = Integer.parseInt( request.queryParams( "clientId" ) );
            if ( reverseClientMap.containsKey( cid ) ) {
                if ( type.equalsIgnoreCase( ClientType.BROWSER.name() ) ) {
                    reverseClientMap.get( cid ).setClientType( ClientType.BROWSER );
                    log.info( "Set client type: " + ClientType.BROWSER.name() );
                } else if ( type.equalsIgnoreCase( ClientType.BENCHMARKER.name() ) ) {
                    reverseClientMap.get( cid ).setClientType( ClientType.BENCHMARKER );
                    log.info( "Set client type: " + ClientType.BENCHMARKER.name() );
                } else {
                    log.error( "Unknown client type: " + type );
                }
            } else {
                log.error( "Unknown client id: {}", cid );
            }
        } else {
            log.error( "Illegal request for setting client type" );
        }
        return null;
    }


    public static Object getBenchmarkerConnected() {
        for ( Client client : clientMap.values() ) {
            if ( client.getClientType() == ClientType.BENCHMARKER ) {
                return true;
            }
        }
        return false;
    }


    @Data
    static class Client {

        private final Session session;
        private final int clientId;
        @Setter
        private ClientType clientType;


        Client( Session session, int clientId ) {
            this.session = session;
            this.clientId = clientId;
            this.clientType = ClientType.UNKNOWN;
        }

    }


    enum ClientType {
        UNKNOWN, BROWSER, BENCHMARKER
    }

}
