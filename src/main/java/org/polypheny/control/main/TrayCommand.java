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

package org.polypheny.control.main;


import com.github.rvesse.airline.annotations.Command;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import lombok.SneakyThrows;
import org.polypheny.control.control.ServiceManager;


@Command(name = "tray", description = "Start Polypheny Control and add it to the system tray")
public class TrayCommand extends ControlCommand {


    @Override
    public int _run_() {
        //checking for support
        if ( !SystemTray.isSupported() ) {
            System.err.println( "System tray is not supported! Use headless mode instead." );
            System.out.println( "In order to start Polypheny Control in headless mode, specify the argument \"control\"." );
            System.exit( 1 );
        }

        // Get the systemTray of the system
        SystemTray systemTray = SystemTray.getSystemTray();

        // Popup menu
        PopupMenu trayPopupMenu = new PopupMenu();

        // Setting tray icon
        ClassLoader classLoader = this.getClass().getClassLoader();
        Image iconStopped = Toolkit.getDefaultToolkit().getImage( classLoader.getResource( "icon-black.png" ) );
        Image iconRunning = Toolkit.getDefaultToolkit().getImage( classLoader.getResource( "icon.png" ) );
        TrayIcon trayIcon = new TrayIcon( iconStopped, "Polypheny Control", trayPopupMenu );
        trayIcon.setImageAutoSize( true ); // Adjust to default size as per system recommendation

        // Set application icon (displayed in some os in notifications)
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            // Set icon for macOS (and other systems which do support this method)
            taskbar.setIconImage( iconRunning );
        } catch ( final UnsupportedOperationException e ) {
            System.out.println( "The os does not support: 'taskbar.setIconImage'" );
        } catch ( final SecurityException e ) {
            System.out.println( "There was a security exception for: 'taskbar.setIconImage'" );
        }

        // Add status text
        MenuItem statusItem = new MenuItem( "???" );
        statusItem.setEnabled( false );
        trayPopupMenu.add( statusItem );

        // Add separator
        trayPopupMenu.addSeparator();

        // Start Polypheny-DB
        MenuItem startItem = new MenuItem( "Start" );
        startItem.addActionListener( e -> ServiceManager.start( null, true ) );
        trayPopupMenu.add( startItem );

        // Stop Polypheny-DB
        MenuItem stopItem = new MenuItem( "Stop" );
        stopItem.addActionListener( e -> ServiceManager.stop( null ) );
        trayPopupMenu.add( stopItem );

        // Dashboard
        MenuItem dashboardItem = new MenuItem( "Dashboard" );
        dashboardItem.addActionListener( new ActionListener() {
            @SneakyThrows
            @Override
            public void actionPerformed( ActionEvent e ) {
                Desktop.getDesktop().browse( new URI( "http://localhost:8070" ) );
            }
        } );
        trayPopupMenu.add( dashboardItem );

        // Add separator
        trayPopupMenu.addSeparator();

        // Open Polypheny-UI in the browser
        MenuItem puiItem = new MenuItem( "Polypheny-UI" );
        puiItem.addActionListener( new ActionListener() {
            @SneakyThrows
            @Override
            public void actionPerformed( ActionEvent e ) {
                Desktop.getDesktop().browse( new URI( "http://localhost:7659" ) );
            }
        } );
        trayPopupMenu.add( puiItem );

        // Add separator
        trayPopupMenu.addSeparator();

        // Quit option
        MenuItem quiteItem = new MenuItem( "Quit" );
        quiteItem.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed( ActionEvent e ) {
                systemTray.remove( trayIcon );
                System.exit( 0 );
            }
        } );
        trayPopupMenu.add( quiteItem );

        // Add to the system tray
        try {
            systemTray.add( trayIcon );
        } catch ( AWTException awtException ) {
            awtException.printStackTrace();
        }

        // Initialize notification manager
        NotificationManager.setTrayIcon( trayIcon );

        // This tasks periodically updates the status of the menu
        TimerTask menuUpdateTask = new TimerTask() {
            @Override
            public void run() {
                String status = (String) ServiceManager.getStatus();
                if ( status.equals( "running" ) ) {
                    statusItem.setLabel( "Running..." );
                    startItem.setEnabled( false );
                    stopItem.setEnabled( true );
                    puiItem.setEnabled( true );
                    trayIcon.setImage( iconRunning );
                } else if ( status.equals( "updating" ) ) {
                    statusItem.setLabel( "Updating..." );
                    startItem.setEnabled( false );
                    stopItem.setEnabled( false );
                    puiItem.setEnabled( false );
                    trayIcon.setImage( iconStopped );
                } else if ( status.equals( "idling" ) ) {
                    statusItem.setLabel( "Stopped..." );
                    startItem.setEnabled( true );
                    stopItem.setEnabled( false );
                    puiItem.setEnabled( false );
                    trayIcon.setImage( iconStopped );
                }
            }
        };
        Timer menuUpdateTimer = new Timer();
        menuUpdateTimer.scheduleAtFixedRate( menuUpdateTask, 0, 1000 );

        // Output message to the user
        NotificationManager.info( "has been added to your system tray." );

        int exitCode = 1;
        try {
            exitCode = super._run_();
        } catch ( RuntimeException e ) {
            NotificationManager.error( e.getMessage() );
        }
        // super._run_() is blocking. This means, the programm has been terminated. Shutdown
        System.exit( exitCode );
        return exitCode;
    }


}
