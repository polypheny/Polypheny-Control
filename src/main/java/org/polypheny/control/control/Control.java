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

package org.polypheny.control.control;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.javalin.http.Context;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import lombok.NonNull;
import lombok.val;
import org.polypheny.control.httpinterface.ClientCommunicationStream;


public class Control {

    private final Gson gson = new Gson();


    public synchronized void getCurrentConfigAsJson( Context ctx ) {
        ctx.result( gson.toJson( ConfigManager.convertToProperties( ConfigManager.getConfig() ) ) );
    }


    public synchronized void setConfig( Context ctx ) {
        getClientCommunicationStream( ctx, "currentConfig" );

        val json = ctx.formParam( "config" );

        JsonObject object = JsonParser.parseString( json ).getAsJsonObject();

        Config newConfig = ConfigFactory.empty();
        for ( Map.Entry<String, JsonElement> entry : object.entrySet() ) {
            if ( entry.getValue().isJsonArray() ) {
                JsonArray array = entry.getValue().getAsJsonArray();
                LinkedList<String> list = new LinkedList<>();
                for ( JsonElement element : array ) {
                    if ( element.isJsonPrimitive() ) {
                        list.add( element.getAsString() );
                    } else {
                        throw new RuntimeException( "Unsupported construct" );
                    }
                }
                newConfig = newConfig.withValue( entry.getKey(), ConfigValueFactory.fromIterable( list ) );
            } else if ( entry.getValue().isJsonObject() ) {
                JsonObject jsonObject = entry.getValue().getAsJsonObject();
                LinkedList<String> list = new LinkedList<>();
                for ( Entry<String, JsonElement> element : jsonObject.entrySet() ) {
                    if ( element.getValue().isJsonPrimitive() ) {
                        list.add( element.getValue().getAsString() );
                    } else {
                        throw new RuntimeException( "Unsupported construct" );
                    }
                }
                newConfig = newConfig.withValue( entry.getKey(), ConfigValueFactory.fromIterable( list ) );
            } else if ( entry.getValue().isJsonPrimitive() ) {
                newConfig = newConfig.withValue( entry.getKey(), ConfigValueFactory.fromAnyRef( entry.getValue().getAsString() ) );
            } else {
                throw new RuntimeException( "Unsupported construct" );
            }
        }
        ConfigManager.writeConfiguration( newConfig );

        ctx.result( gson.toJson( true ) );
    }


    public void start( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.start( getClientCommunicationStream( ctx, "logOutput" ) ) ) );
    }


    public void stop( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.stop( getClientCommunicationStream( ctx, "logOutput" ) ) ) );
    }


    public void restart( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.restart( getClientCommunicationStream( ctx, "logOutput" ) ) ) );
    }


    public void update( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.update( getClientCommunicationStream( ctx, "updateOutput" ) ) ) );
    }


    public void getStatus( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.getStatus() ) );
    }


    public void getVersion( Context ctx ) {
        ctx.result( gson.toJson( ServiceManager.getVersion() ) );
    }


    public void getControlVersion( Context ctx ) {
        String v = Control.class.getPackage().getImplementationVersion();
        if ( v == null ) {
            ctx.result( "Unknown" );
        } else {
            ctx.result( v );
        }
    }


    public void getAvailablePdbBranches( Context ctx ) {
        val configuration = ConfigManager.getConfig();
        val pdbbuilddir = new File( configuration.getString( "pcrtl.pdbbuilddir" ) );
        if ( !pdbbuilddir.exists() ) {
            ServiceManager.clonePdbRepository( null, configuration );
        }
        List<String> list = ServiceManager.getAvailableBranches( pdbbuilddir );
        ctx.result( gson.toJson( list ) );
    }


    public void getAvailablePuiBranches( Context ctx ) {
        val configuration = ConfigManager.getConfig();
        val puiBuildDir = new File( configuration.getString( "pcrtl.puibuilddir" ) );
        if ( !puiBuildDir.exists() ) {
            ServiceManager.clonePuiRepository( null, configuration );
        }
        List<String> list = ServiceManager.getAvailableBranches( puiBuildDir );
        ctx.result( gson.toJson( list ) );
    }


    private ClientCommunicationStream getClientCommunicationStream( @NonNull final Context context, @NonNull final String topic ) {
        String str = context.formParam( "clientId" );
        if ( str != null ) {
            val cid = Integer.parseInt( str );
            return new ClientCommunicationStream( cid, topic );
        }
        throw new NoSuchElementException( "The request does not contain a client identifier (clientId)" );
    }
}
