package io.compgen.ngsutils.cli.bam;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.bam.support.BAIFile;

@Command(name="bai-explore", desc="Explore the layout of a BAI file", category="bam", experimental=true, hidden=true)
public class BaiExplore extends AbstractOutputCommand {
    private String filename = null;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }


    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAI filename!");
        }
        
        new BAIFile(filename);
        
    }
}
