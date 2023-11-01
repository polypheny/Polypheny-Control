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

package org.polypheny.control.authentication;


import com.typesafe.config.Config;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.polypheny.control.control.ConfigManager;


public class AuthenticationUtils {

    private static boolean authenticationEnabled;
    private static boolean localAuthenticationEnabled;
    private static boolean cliAuthenticationEnabled;


    static {
        Config config = ConfigManager.getConfig();
        authenticationEnabled = config.getBoolean( "pcrtl.auth.enable" );
        localAuthenticationEnabled = authenticationEnabled && config.getBoolean( "pcrtl.auth.local" );
        cliAuthenticationEnabled = authenticationEnabled && config.getBoolean( "pcrtl.auth.cli" );

        if ( authenticationEnabled && System.getProperty( "config.auth.local" ) != null ) {
            localAuthenticationEnabled = System.getProperty( "config.auth.local" ).equals( "true" );
        }
    }


    public static boolean shouldAuthenticate( AuthenticationContext context ) {
        if ( context == AuthenticationContext.REMOTEHOST ) {
            return authenticationEnabled;
        } else if ( context == AuthenticationContext.LOCALHOST ) {
            return localAuthenticationEnabled;
        } else if ( context == AuthenticationContext.CLI ) {
            return cliAuthenticationEnabled;
        } else {
            throw new RuntimeException( "Unknown Authentication Context: " + context.name() );
        }
    }


    public static AuthenticationContext getContextForHost( String host ) {
        try {
            InetAddress clientIPAddress = InetAddress.getByName( host );
            InetAddress serverIPAddress = InetAddress.getLocalHost();
            if ( clientIPAddress.isLoopbackAddress() || clientIPAddress.equals( serverIPAddress ) ) {
                return AuthenticationContext.LOCALHOST;
            } else {
                return AuthenticationContext.REMOTEHOST;
            }
        } catch ( UnknownHostException e ) {
            throw new RuntimeException( "Cannot resolve host: " + host );
        }
    }

}
