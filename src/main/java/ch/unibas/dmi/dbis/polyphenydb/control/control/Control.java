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

package ch.unibas.dmi.dbis.polyphenydb.control.control;


import ch.unibas.dmi.dbis.polyphenydb.control.httpinterface.ClientCommunicationStream;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import lombok.NonNull;
import lombok.val;
import spark.Request;
import spark.Response;


public class Control {

    private final Gson gson = new Gson();


    public synchronized String getCurrentConfigAsJson( final Request request, final Response response ) {
        return gson.toJson( ConfigManager.convertToProperties( ConfigManager.getConfig() ) );
    }


    public synchronized boolean setConfig( final Request request, final Response response ) {
        getClientCommunicationStream( request, "currentConfig" );

        val json = request.queryMap( "config" ).value();

        JsonParser parser = new JsonParser();
        JsonObject object = parser.parse( json ).getAsJsonObject();

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

        return true;
    }


    public boolean start( final Request request, final Response response ) {
        return ServiceManager.start( getClientCommunicationStream( request, "logOutput" ) );
    }


    public boolean stop( final Request request, final Response response ) {
        return ServiceManager.stop( getClientCommunicationStream( request, "logOutput" ) );
    }


    public boolean restart( final Request request, final Response response ) {
        return ServiceManager.restart( getClientCommunicationStream( request, "logOutput" ) );
    }


    public boolean update( final Request request, final Response response ) {
        return ServiceManager.update( getClientCommunicationStream( request, "updateOutput" ) );
    }


    public boolean getStatus( final Request request, final Response response ) {
        return false;
    }


    public Object getVersion( final Request request, final Response response ) {
        return ServiceManager.getVersion();
    }


    private ClientCommunicationStream getClientCommunicationStream( @NonNull final Request request, @NonNull final String topic ) {
        if ( request.queryParams().contains( "clientId" ) ) {
            val cid = Integer.parseInt( request.queryParams( "clientId" ) );
            return new ClientCommunicationStream( cid, topic );
        }
        throw new NoSuchElementException( "The request does not contain a client identifier (clientId)" );
    }
}
