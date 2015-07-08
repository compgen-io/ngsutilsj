package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.progress.ProgressMessage;

import java.io.IOException;

@Command(name="fasta-wrap", desc="Change the sequence wrapping length of a FASTA file", category="fasta")
public class FastaWrap extends AbstractOutputCommand {
    private String filename = null;
    private int wrap = 60;
    private boolean nowrap = false;
    
    @Option(name="wrap", charName="w", desc="Wrap output sequences", helpValue="len", defaultValue="60")
    public void setWrap(int wrap) {
        this.wrap = wrap;
    }

    @Option(name="nowrap", desc="Remove all wrapping (one sequence per line, regardless of length)")
    public void setNowrap(boolean nowrap) {
        this.nowrap = nowrap;
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        
        final String[] current = new String[1];

        String buffer = "";
        
        for (String line: IterUtils.wrap(new StringLineReader(filename).progress(new ProgressMessage<String>(){
                @Override
                public String msg(String s) {
                    return current[0];
                }
            }))) {
            if (line.charAt(0) == '>') {
                if (!buffer.equals("")) {
                    out.write((buffer+"\n").getBytes());
                    buffer = "";
                }
                current[0] = line.substring(1).split("\\W",2)[0];
                out.write((line+"\n").getBytes());
            } else {
                buffer += line;
                if (!nowrap) {
                    while (buffer.length() > wrap) {
                        out.write((buffer.substring(0, wrap)+"\n").getBytes());
                        buffer = buffer.substring(wrap);
                    }
                }
            }
        }
        if (!buffer.equals("")) {
            out.write((buffer+"\n").getBytes());
        }
    }
}
