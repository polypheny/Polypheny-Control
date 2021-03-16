package org.polypheny.control.authentication;


import java.util.HashMap;
import org.apache.commons.codec.digest.Crypt;


public class AuthenticationManager {

    public static boolean clientExists( String name, String password ) {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        String encryptedPassword = authenticationData.get( name );
        return encryptedPassword != null && encryptedPassword.equals( Crypt.crypt( password, encryptedPassword ) );
    }
}
