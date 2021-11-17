/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.control.client;

import kong.unirest.Cookie;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.MultipartBody;
import kong.unirest.Unirest;


interface POSTComposer {

    MultipartBody composeRequest( HttpRequestWithBody request );

}


interface SessionTimeoutHandler {

    boolean handleSessionTimeout();

}


public class HttpConnector {

    private Cookie jsessionid;
    private SessionTimeoutHandler sessionTimeoutHandler;


    public HttpConnector() {
        Unirest.config().connectTimeout( 0 );
        Unirest.config().socketTimeout( 0 );
        Unirest.config().concurrency( 200, 100 );
    }


    public void authenticate( String url, String username, String password ) {
        HttpResponse<String> response = Unirest.get( url )
                .basicAuth( username, password )
                .asString();
        jsessionid = response.getCookies().getNamed( "JSESSIONID" );
    }


    public HttpResponse<String> post( String url, POSTComposer composer ) {
        HttpRequestWithBody request = Unirest.post( url );
        MultipartBody multipartBody = composer.composeRequest( request );
        ensureJSESSIONIDAttached( multipartBody );
        HttpResponse<String> response = multipartBody.asString();

        // Handle Session Timeout
        if ( response.getStatus() == 401 ) {
            boolean shouldResendRequest = sessionTimeoutHandler.handleSessionTimeout();
            if ( shouldResendRequest ) {
                return post( url, composer );
            }
        }

        return response;
    }


    public HttpResponse<String> get( String url ) {
        GetRequest request = Unirest.get( url );
        ensureJSESSIONIDAttached( request );
        HttpResponse<String> response = request.asString();

        // Handle Session Timeout
        if ( response.getStatus() == 401 ) {
            boolean shouldResendRequest = sessionTimeoutHandler.handleSessionTimeout();
            if ( shouldResendRequest ) {
                return get( url );
            }
        }

        return response;
    }


    public void setSessionTimeoutHandler( SessionTimeoutHandler sessionTimeoutHandler ) {
        this.sessionTimeoutHandler = sessionTimeoutHandler;
    }


    private void ensureJSESSIONIDAttached( HttpRequest request ) {
        if ( jsessionid != null ) {
            request.cookie( jsessionid );
        }
    }

}
