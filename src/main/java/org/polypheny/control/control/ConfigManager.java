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


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;


@Slf4j
public class ConfigManager {

    private static Config currentConfig = null;

    private static File applicationConfFile = null;


    public static Config getConfig() {
        if ( currentConfig == null ) {
            loadConfigFile();
        }
        return currentConfig;
    }


    private static void loadConfigFile() {
        if ( applicationConfFile == null ) {
            val defaultConfig = ConfigFactory.load();
            val workingDir = defaultConfig.getString( "pcrtl.workingdir" );
            val configDir = new File( new File( workingDir ), "config" );
            createConfigFolders( workingDir, configDir );
            applicationConfFile = new File( configDir, "application.conf" );
        }

        currentConfig = ConfigFactory.parseFile( applicationConfFile ).withFallback( ConfigFactory.defaultReference() );
    }


    private static void createConfigFolders( String workingDir, File configDir ) {
        if ( !new File( workingDir ).exists() ) {
            if ( !new File( workingDir ).mkdirs() ) {
                throw new RuntimeException( "Could not create the folders for " + new File( workingDir ).getAbsolutePath() );
            }
        }
        if ( !configDir.exists() ) {
            if ( !configDir.mkdirs() ) {
                throw new RuntimeException( "Could not create the config folder: " + configDir.getAbsolutePath() );
            }
        }
    }


    public static void writeConfiguration( final Config configuration ) {
        ConfigRenderOptions configRenderOptions = ConfigRenderOptions.defaults();
        configRenderOptions = configRenderOptions.setComments( false );
        configRenderOptions = configRenderOptions.setFormatted( true );
        configRenderOptions = configRenderOptions.setJson( false );
        configRenderOptions = configRenderOptions.setOriginComments( false );

        val defaultConfig = ConfigFactory.load();
        val workingDir = defaultConfig.getString( "pcrtl.workingdir" );
        val configDir = new File( new File( workingDir ), "config" );
        createConfigFolders( workingDir, configDir );
        try (
                FileOutputStream fos = new FileOutputStream( applicationConfFile, false );
                BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( fos ) )
        ) {
            bw.write( configuration.root().render( configRenderOptions ) );
        } catch ( IOException e ) {
            log.error( "Exception while writing configuration file", e );
        }
        loadConfigFile();
    }


    public static void setApplicationConfFile( File applicationConfFile ) {
        ConfigManager.applicationConfFile = applicationConfFile;
    }


    public static String getCurrentConfigAsJson() {
        val config = getConfig();
        ConfigRenderOptions configRenderOptions = ConfigRenderOptions.defaults();
        configRenderOptions = configRenderOptions.setComments( false );
        configRenderOptions = configRenderOptions.setFormatted( true );
        configRenderOptions = configRenderOptions.setJson( true );
        configRenderOptions = configRenderOptions.setOriginComments( false );
        return config.root().render( configRenderOptions );
    }


    public static Properties convertToProperties( Config config ) {
        val properties = new Properties();
        for ( val entry : config.entrySet() ) {
            val key = entry.getKey();
            val value = entry.getValue();

            final String stringValue;
            switch ( value.valueType() ) {
                default:
                case OBJECT:
                    stringValue = value.render();
                    break;

                case LIST:
                    List l = (List) value.unwrapped();
                    String str = "[\"" + StringUtils.join( l.listIterator(), "\", \"" ) + "\"]";
                    // If the string is already wrapped in double quotes in the config file, we now have to many quotes. This is very ugly..
                    stringValue = str.replace( "\"\"", "\"" );
                    break;

                case NUMBER:
                case BOOLEAN:
                case STRING:
                    stringValue = value.unwrapped().toString();
                    break;

                case NULL:
                    stringValue = null;
                    break;
            }

            properties.setProperty( key, stringValue );
        }
        return properties;
    }

}
