package org.polypheny.control.authentication;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import lombok.val;
import org.polypheny.control.control.ConfigManager;


public class AuthenticationFileManager {

    private static File authenticationFile;
    private static HashMap<String, String> authenticationFileData;


    // Key is Name; Value is Encrypted Password
    public static HashMap<String, String> getAuthenticationFileData() {
        if ( authenticationFileData == null ) {
            val config = ConfigManager.getConfig();
            String workingDir = config.getString( "pcrtl.workingdir" );
            authenticationFile = new File( workingDir, "passwd" );
            authenticationFileData = new HashMap<>();
            try ( FileReader fileReader = new FileReader( authenticationFile );
                    BufferedReader bufferedReader = new BufferedReader( fileReader ) ) {
                bufferedReader.lines().map( line -> line.split( "\\s" ) ).forEach( data -> {
                    if ( data.length == 2 ) {
                        authenticationFileData.put( data[0], data[1] );
                    } else {
                        // Happens only if file format was changed
                        // TODO: Throw exception
                    }
                } );
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
        return authenticationFileData;
    }


    public static void setAuthenticationFileData( HashMap<String, String> _authenticationFileData ) {
        if ( _authenticationFileData == null ) {
            throw new NullPointerException( "Data to be written cannot be null." );
        }
        if ( authenticationFile == null ) {
            val config = ConfigManager.getConfig();
            String workingDir = config.getString( "pcrtl.workingdir" );
            authenticationFile = new File( workingDir, "passwd" );
        }
        authenticationFileData = _authenticationFileData;
        try ( FileWriter fileWriter = new FileWriter( authenticationFile );
                BufferedWriter bufferedWriter = new BufferedWriter( fileWriter ) ) {
            for ( Entry<String, String> entry : authenticationFileData.entrySet() ) {
                String name = entry.getKey();
                String password = entry.getValue();
                bufferedWriter.write( name + " " + password + "\n" );
            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
}
