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


import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import java.io.File;
import javax.inject.Inject;
import org.polypheny.control.control.ConfigManager;


public abstract class AbstractCommand implements CliRunnable {

    @Inject
    protected HelpOption<ControlCommand> help;

    @Option(name = { "-c", "--config" }, description = "Path to the configuration file", type = OptionType.GLOBAL)
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
