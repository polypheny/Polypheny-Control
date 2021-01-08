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


import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.model.GlobalMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;


@Command(name = "help", description = "A command that provides help on other commands")
public class HelpCommand implements CliRunnable {

    @Inject
    private GlobalMetadata<CliRunnable> global;

    @Arguments(description = "Provides the name of the commands you want to provide help for")
    private List<String> commandNames = new ArrayList<>();

    @Option(name = "--include-hidden", description = "When set hidden commands and options are shown in help", hidden = true)
    private boolean includeHidden = false;


    @Override
    public int run() {
        try {
            com.github.rvesse.airline.help.Help.help( global, commandNames, this.includeHidden );
        } catch ( IOException e ) {
            System.err.println( "Failed to output help: " + e.getMessage() );
            e.printStackTrace( System.err );
            return 1;
        }
        return 0;
    }

}
