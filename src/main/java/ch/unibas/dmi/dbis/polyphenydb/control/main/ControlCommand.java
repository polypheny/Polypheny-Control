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

package ch.unibas.dmi.dbis.polyphenydb.control.main;


import ch.unibas.dmi.dbis.polyphenydb.control.control.ConfigManager;
import ch.unibas.dmi.dbis.polyphenydb.control.control.Control;
import ch.unibas.dmi.dbis.polyphenydb.control.httpinterface.Server;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;


@Command(name = "control", description = "Start Polypheny Control")
public class ControlCommand extends AbstractCommand {

    @Option(name = { "-p", "--port" }, description = "Port")
    private int port = -1;

    private volatile boolean running = true;


    @Override
    public int _run_() {
        Control control = new Control();
        final Server server;
        if ( port > 0 ) {
            server = new Server( control, port );
        } else {
            server = new Server( control, ConfigManager.getConfig().getInt( "pcrtl.control.port" ) );
        }

        Runtime.getRuntime().addShutdownHook( new Thread( () -> running = false ) );

        while ( running ) {
            Thread.yield();
            try {
                Thread.sleep(1000);
            } catch ( InterruptedException e ) {
                // ignore
            }
        }

        return 0;
    }

}
