package org.ngsutils.cli;

import com.lexicalscope.jewel.cli.Option;

public abstract class AbstractCommand implements Exec {
    protected boolean verbose = false;

    @Option(helpRequest = true, description = "Display help", shortName = "h")
    public void setHelp(boolean help) {
    }
    @Option(description = "Verbose output", shortName = "v")
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
