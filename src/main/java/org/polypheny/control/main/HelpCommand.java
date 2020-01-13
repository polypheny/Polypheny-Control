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


import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.model.GlobalMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;


@SuppressWarnings("FieldCanBeLocal")
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
