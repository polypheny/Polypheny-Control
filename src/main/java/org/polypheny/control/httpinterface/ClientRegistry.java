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

package org.polypheny.control.httpinterface;


import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.polypheny.control.client.ClientType;
import org.polypheny.control.control.ServiceManager;


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
            log.debug( "Send message to client {}: topic: {} | message: {}", client.getClientId(), topic, message );
            session.getRemote().sendString( String.valueOf( gson.toJson( map ) ) );
        } catch ( Exception e ) {
            log.debug( "Exception thrown while sending message to client {}", client.getClientId(), e );
        }
    }


    private static void sendMessage( Session session, String topic, Map<String, String> msgMap ) {
        Client client = clientMap.get( session );
        Map<String, Map<String, String>> map = new HashMap<>();
        map.put( topic, msgMap );
        try {
            log.debug( "Send message to client {}: topic: {} | message: (MAP)", client.getClientId(), topic );
            session.getRemote().sendString( String.valueOf( gson.toJson( map ) ) );
        } catch ( Exception e ) {
            log.debug( "Exception thrown while sending message to client {}", client.getClientId(), e );
        }
    }


    static synchronized void addClient( WsConnectContext ctx ) {
        int cid = nextClientNumber++;
        Client client = new Client( ctx.session, cid );
        clientMap.put( ctx.session, client );
        reverseClientMap.put( cid, client );
        sendMessage( cid, "clientId", "" + cid );
        log.info( "Registered client {} from IP {}", cid, ctx.session.getRemoteAddress().getAddress().getHostAddress() );
        sendMessage( cid, "status", "" + ServiceManager.getStatus() );
        sendMessage( cid, "benchmarkerConnected", "" + ClientRegistry.getBenchmarkerConnected() );
        sendMessage( cid, "version", ServiceManager.getVersion() );
    }


    static void removeClient( WsCloseContext closeContext ) {
        Client client = clientMap.remove( closeContext.session );
        reverseClientMap.remove( client.clientId );
        log.info( "Removed client {} from IP {}", client.clientId, closeContext.session.getRemoteAddress().getAddress().getHostAddress() );
    }


    public static void setClientType( Context ctx ) {
        String type = ctx.formParam( "clientType" );
        String cidStr = ctx.formParam( "clientId" );
        if ( type != null && cidStr != null ) {
            int cid = Integer.parseInt( cidStr );
            if ( reverseClientMap.containsKey( cid ) ) {
                if ( type.equalsIgnoreCase( ClientType.BROWSER.name() ) ) {
                    reverseClientMap.get( cid ).setClientType( ClientType.BROWSER );
                    log.info( "Set client type: {}", ClientType.BROWSER.name() );
                } else if ( type.equalsIgnoreCase( ClientType.BENCHMARKER.name() ) ) {
                    reverseClientMap.get( cid ).setClientType( ClientType.BENCHMARKER );
                    log.info( "Set client type: {}", ClientType.BENCHMARKER.name() );
                } else {
                    log.error( "Unknown client type: {}", type );
                }
            } else {
                log.error( "Unknown client id: {}", cid );
            }
        } else {
            log.error( "Illegal request for setting client type" );
        }
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

}
