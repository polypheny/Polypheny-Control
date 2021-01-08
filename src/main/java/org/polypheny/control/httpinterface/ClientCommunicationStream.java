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


public class ClientCommunicationStream {

    private final int clientId;
    private final String topic;


    public ClientCommunicationStream( int clientId, String topic ) {
        this.clientId = clientId;
        this.topic = topic;
    }


    public void send( CharSequence csq ) {
        if ( csq == null ) {
            //ClientRegistry.sendMessage( clientId, topic, "null" );
            ClientRegistry.broadcast( topic, "null" );
        } else {
            //ClientRegistry.sendMessage( clientId, topic, csq.toString() );
            ClientRegistry.broadcast( topic, csq.toString() );
        }
    }
}
