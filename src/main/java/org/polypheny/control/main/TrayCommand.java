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

package org.polypheny.control.main;


import com.github.rvesse.airline.annotations.Command;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import lombok.SneakyThrows;
import org.polypheny.control.control.ServiceManager;


@Command(name = "tray", description = "Adds Polypheny Control to the system tray")
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

        // TODO: This requires Java >= 9
        // Set application icon (displayed in some os in notifications)
        //try {
        //    Taskbar taskbar = Taskbar.getTaskbar();
        //    // set icon for mac os (and other systems which do support this method)
        //    taskbar.setIconImage( iconRunning );
        //} catch (final UnsupportedOperationException e) {
        //    System.out.println("The os does not support: 'taskbar.setIconImage'");
        //} catch (final SecurityException e) {
        //    System.out.println("There was a security exception for: 'taskbar.setIconImage'");
        //}

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

        // Open Polypheny-Control UI in the browser
        MenuItem puiItem = new MenuItem( "Polypheny-UI" );
        puiItem.addActionListener( new ActionListener() {
            @SneakyThrows
            @Override
            public void actionPerformed( ActionEvent e ) {
                Desktop.getDesktop().browse( new URI( "http://localhost:8080" ) );
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
        trayIcon.displayMessage( "Polypheny Control", "has been added to your system tray.", MessageType.INFO );

        return super._run_();
    }


}
