package org.polypheny.control.main;


import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import lombok.Setter;


public class NotificationManager {

    @Setter
    private static TrayIcon trayIcon;


    public static void info( String message ) {
        if ( trayIcon != null ) {
            trayIcon.displayMessage( "Polypheny Control", message, MessageType.INFO );
        }
    }


    public static void warning( String message ) {
        if ( trayIcon != null ) {
            trayIcon.displayMessage( "Polypheny Control", message, MessageType.WARNING );
        }
    }


    public static void error( String message ) {
        if ( trayIcon != null ) {
            trayIcon.displayMessage( "Polypheny Control", message, MessageType.ERROR );
        }
    }
}
