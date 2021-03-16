package org.polypheny.control.authentication;


import java.security.SecureRandom;
import java.util.HashMap;
import org.apache.commons.codec.digest.Crypt;


public class AuthenticationDataManager {

    private static String generateRandomSalt() {
        String characters = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder salt = new StringBuilder( "$6$" );    // Using SHA512
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[13];
        random.nextBytes( randomBytes );
        for ( byte randomByte : randomBytes ) {
            int index = ( randomByte < 0 ? -1 * randomByte : randomByte ) % characters.length();
            salt.append( characters.charAt( index ) );
        }
        return salt.toString();
    }


    private static String encryptPassword( String password ) {
        String salt = generateRandomSalt();
        return Crypt.crypt( password, salt );
    }


    public static void addAuthenticationData( String name, String password ) {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        authenticationData.put( name, encryptPassword( password ) );
    }


    public static void removeAuthenticationData( String name ) {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        authenticationData.remove( name );
    }


    public static void modifyAuthenticationData( String name, String password ) {
        HashMap<String, String> authenticationData = AuthenticationFileManager.getAuthenticationData();
        authenticationData.replace( name, encryptPassword( password ) );
    }
}
