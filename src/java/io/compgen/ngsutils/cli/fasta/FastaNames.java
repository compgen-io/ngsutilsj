package io.compgen.ngsutils.cli.fasta;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;

@Command(name="fasta-names", desc="Display sequence names from a FASTA file", category="fasta")
public class FastaNames extends AbstractOutputCommand {
    
    private String filename = null;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        
        StringLineReader reader = new StringLineReader(filename);
        for (String line: IterUtils.wrap(reader.iterator())) {
            if (line.charAt(0) == '>') {
                String name = line.substring(1).split("\\W",2)[0];
                out.write((name+"\n").getBytes());
            }
        }
        reader.close();
    }
}
