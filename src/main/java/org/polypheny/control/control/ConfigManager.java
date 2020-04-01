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

            if ( new File( workingDir ).exists() == false ) {
                if ( new File( workingDir ).mkdirs() == false ) {
                    throw new RuntimeException( "Could not create the folders for " + new File( workingDir ).getAbsolutePath() );
                }
            }

            val configDir = new File( new File( workingDir ), "config" );
            if ( configDir.exists() == false ) {
                if ( configDir.mkdirs() == false ) {
                    throw new RuntimeException( "Could not create the config folder: " + configDir.getAbsolutePath() );
                }
            }

            applicationConfFile = new File( configDir, "application.conf" );
        }

        currentConfig = ConfigFactory.parseFile( applicationConfFile ).withFallback( ConfigFactory.defaultReference() );
    }


    public static void writeConfiguration( final Config configuration ) {
        ConfigRenderOptions configRenderOptions = ConfigRenderOptions.defaults();
        configRenderOptions = configRenderOptions.setComments( false );
        configRenderOptions = configRenderOptions.setFormatted( true );
        configRenderOptions = configRenderOptions.setJson( false );
        configRenderOptions = configRenderOptions.setOriginComments( false );

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
