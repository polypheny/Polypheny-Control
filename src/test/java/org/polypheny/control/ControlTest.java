/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.control;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.polypheny.control.authentication.AuthenticationDataManager;
import org.polypheny.control.authentication.AuthenticationFileManager;
import org.polypheny.control.client.ClientData;
import org.polypheny.control.client.ClientType;
import org.polypheny.control.client.PolyphenyControlConnector;
import org.polypheny.control.control.ConfigManager;
import org.polypheny.control.main.ControlCommand;


@Slf4j
public class ControlTest {

    private static Thread thread;
    private volatile static Boolean running = true;

    @BeforeAll
    public static void start() throws InterruptedException {
        // Setting the systemProperty 'testing'
        System.setProperty( "testing", "true" );

        // Backup .pcontrol folder
        File pcontrolDir = new File( ConfigManager.getConfig().getString( "pcrtl.workingdir" ) );
        if ( pcontrolDir.exists() ) {
            pcontrolDir.renameTo( new File( ConfigManager.getConfig().getString( "pcrtl.workingdir" ) + ".backup" ) );
        }

        // Backup .polypheny folder
        File polyphenyDir = new File( System.getProperty( "user.home" ), ".polypheny" );
        if ( polyphenyDir.exists() ) {
            polyphenyDir.renameTo( new File( System.getProperty( "user.home" ), ".polypheny.backup" ) );
        }

        // Create passwd data and an account
        try {
            FileUtils.forceMkdir( new File( ConfigManager.getConfig().getString( "pcrtl.workingdir" ) ) );
        } catch ( IOException e ) {
            log.error( "Caught exception while creating .pcrtl folder", e );
        }
        AuthenticationFileManager.getAuthenticationData();
        AuthenticationDataManager.addAuthenticationData( "pc", "super$secret" );
        AuthenticationFileManager.writeAuthenticationDataToFile();

        thread = new Thread( () -> (new ControlCommand()).runWithControlledShutdown( running ) );
        thread.start();
        TimeUnit.SECONDS.sleep( 5 );
    }


    @AfterAll
    public static void shutdown() throws IOException {
        running = false;

        // Restore .polypheny
        File polyphenyDir = new File( System.getProperty( "user.home" ), ".polypheny" );
        FileUtils.deleteDirectory( polyphenyDir );
        File polyphenyDirBackup = new File( System.getProperty( "user.home" ), ".polypheny.backup" );
        if ( polyphenyDirBackup.exists() ) {
            polyphenyDirBackup.renameTo( polyphenyDir );
        }

        // Restore .pcontrol
        File pcontrolDir = new File( ConfigManager.getConfig().getString( "pcrtl.workingdir" ) );
        FileUtils.deleteDirectory( pcontrolDir );
        File pcontrolBackupDir = new File( ConfigManager.getConfig().getString( "pcrtl.workingdir" ) + ".backup" );
        if ( pcontrolBackupDir.exists() ) {
            pcontrolBackupDir.renameTo( pcontrolDir );
        }
    }


    @Test
    public void integrationTest() throws URISyntaxException, InterruptedException {
        ClientData clientData = new ClientData( ClientType.BROWSER, "pc", "super$secret" );
        PolyphenyControlConnector controlConnector = new PolyphenyControlConnector( "localhost:8070", clientData, null );

        // Purge polypheny folder
        controlConnector.purgePolyphenyFolder();

        // Update and build Polypheny
        controlConnector.updatePolypheny();

        // Start Polypheny
        controlConnector.startPolypheny();
        TimeUnit.SECONDS.sleep( 30 );

        // Execute test query
        GetRequest request = Unirest.get( "{protocol}://{host}:{port}/restapi/v1/res/public.emps" )
                .queryString( "public.emps.empid", "=" + 100 );
        Assertions.assertEquals(
                "{\"result\":[{\"public.emps.name\":\"Bill\",\"public.emps.salary\":10000,\"public.emps.empid\":100,\"public.emps.commission\":1000,\"public.emps.deptno\":10}],\"size\":1}",
                executeRest( request ).getBody() );

        // Stop Polypheny
        controlConnector.stopPolypheny();
        TimeUnit.SECONDS.sleep( 15 );
    }


    private HttpResponse<String> executeRest( HttpRequest<?> request ) {
        request.basicAuth( "pa", "" );
        request.routeParam( "protocol", "http" );
        request.routeParam( "host", "127.0.0.1" );
        request.routeParam( "port", "8089" );
        try {
            HttpResponse<String> result = request.asString();
            if ( !result.isSuccess() ) {
                throw new RuntimeException( "Error while executing REST query. Message: " + result.getStatusText() + "  |  URL: " + request.getUrl() );
            }
            return result;
        } catch ( UnirestException e ) {
            throw new RuntimeException( e );
        }
    }

}
