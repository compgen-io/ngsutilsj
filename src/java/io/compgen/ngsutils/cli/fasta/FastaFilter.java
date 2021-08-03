package io.compgen.ngsutils.cli.fasta;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.progress.ProgressMessage;

@Command(name="fasta-filter", desc="Filter out sequences from a FASTA file", category="fasta")
public class FastaFilter extends AbstractOutputCommand {
    private String filename = null;
    private Set<String> include = null;
    private Set<String> exclude = null;
    private int wrap = -1;
    
    @Option(name="include", desc="Include only these sequences (comma-delimited values or filename)", helpValue="val")
    public void setInclude(String fname) throws IOException {
        this.include = new HashSet<String>();
        if (new File(fname).exists()) {
            for (String s: new StringLineReader(fname)) {
                if (!s.equals("") && !s.startsWith("#")) {
                    this.include.add(s);
                }
            }
        } else {
            for (String s: fname.split(",")) {
                this.include.add(s);
            }
        }
    }

    @Option(name="wrap", charName="w", desc="Wrap output sequences", helpValue="len")
    public void setWrap(int wrap) {
        this.wrap = wrap;
    }

    @Option(name="exclude", desc="Exclude these sequences (comma-delimited values or filename)", helpValue="val")
    public void setExclude(String fname) throws IOException {
        this.exclude = new HashSet<String>();
        if (new File(fname).exists()) {
            for (String s: new StringLineReader(fname)) {
                if (!s.equals("") && !s.startsWith("#")) {
                    this.exclude.add(s);
                }
            }
        } else {
            for (String s: fname.split(",")) {
                this.exclude.add(s);
            }
        }
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
        
        if ((include == null && exclude == null) || (include != null && exclude != null)) {
            throw new CommandArgumentException("You must set either --include or --exclude");
        }

        boolean good=true;
        final String[] current = new String[1];

        String buffer = "";
        
        StringLineReader reader = new StringLineReader(filename);
        for (String line: IterUtils.wrap(reader.progress(new ProgressMessage<String>(){
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
                current[0] = line.substring(1).split("\\s",2)[0];
                if (include != null) {
                    good = false;
                    if (include.contains(current[0])) {
                        good = true;
                    }
                } else {
                    good = true;
                    if (exclude.contains(current[0])) {
                        good = false;
                    }
                }
                if (good) {
                    out.write((line+"\n").getBytes());
                }
            } else {
                if (good) {
                    if (wrap > 0) {
                        buffer += line;
                        while (buffer.length() > wrap) {
                            out.write((buffer.substring(0, wrap)+"\n").getBytes());
                            buffer = buffer.substring(wrap);
                        }
                    } else {
                        out.write((line+"\n").getBytes());
                    }
                }
            }
        }
        if (!buffer.equals("")) {
            out.write((buffer+"\n").getBytes());
        }
        reader.close();
    }
}
