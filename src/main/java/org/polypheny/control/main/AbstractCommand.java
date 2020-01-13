/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Databases and Information Systems Research Group, University of Basel, Switzerland
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


import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import java.io.File;
import javax.inject.Inject;
import org.polypheny.control.control.ConfigManager;


public abstract class AbstractCommand implements CliRunnable {

    @Inject
    protected HelpOption<ControlCommand> help;

    @Option(name = { "-c", "--config" }, description = "Path to the configuration file.", type = OptionType.GLOBAL)
    protected String applicationConfPath;// ConfigManager.CONFIG_FILE.getAbsolutePath();


    @Override
    public final int run() {
        if ( applicationConfPath != null ) {
            ConfigManager.setApplicationConfFile( new File( applicationConfPath ) );
        }
        return _run_();
    }


    protected abstract int _run_();
}
