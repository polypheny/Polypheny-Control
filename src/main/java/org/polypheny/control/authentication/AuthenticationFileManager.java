package org.polypheny.control.authentication;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import lombok.val;
import org.polypheny.control.control.ConfigManager;


public class AuthenticationFileManager {

    private static File authenticationFile;
    private static HashMap<String, String> authenticationData;


    private static void loadAuthenticationFile() {
        if ( authenticationFile == null ) {
            val config = ConfigManager.getConfig();
            String workingDir = config.getString( "pcrtl.workingdir" );
            authenticationFile = new File( workingDir, "passwd" );
        }
    }


    private static void loadAuthenticationDataFromFile() {
        if ( authenticationData == null ) {
            loadAuthenticationFile();
            authenticationData = new HashMap<>();
            try ( FileReader fileReader = new FileReader( authenticationFile );
                    BufferedReader bufferedReader = new BufferedReader( fileReader ) ) {
                bufferedReader.lines().map( line -> line.split( "\\s" ) ).forEach( data -> {
                    if ( data.length == 2 ) {
                        authenticationData.put( data[0], data[1] );
                    } else {
                        // Happens only if file format was changed
                        throw new RuntimeException( "Authentication File Data Format Invalid." );
                    }
                } );
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }


    // Key is Name; Value is Encrypted Password
    public static HashMap<String, String> getAuthenticationData() {
        loadAuthenticationDataFromFile();
        return authenticationData;
    }


    public static void writeAuthenticationDataToFile() {
        if ( authenticationData == null ) {
            // Data was never modified. So ignore call.
            return;
        }
        try ( FileWriter fileWriter = new FileWriter( authenticationFile );
                BufferedWriter bufferedWriter = new BufferedWriter( fileWriter ) ) {
            loadAuthenticationFile();
            for ( Entry<String, String> entry : authenticationData.entrySet() ) {
                String name = entry.getKey();
                String password = entry.getValue();
                bufferedWriter.write( name + " " + password + "\n" );
            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
}
