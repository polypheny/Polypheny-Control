package org.polypheny.control.client;

public interface LogHandler {

    void handleLogMessage( String logOutput );

    void handleStartupMessage( String startOutput );

    void handleShutdownMessage( String stopOutput );

    void handleRestartMessage( String restartOutput );

    void handleUpdateMessage( String logStr );

}
