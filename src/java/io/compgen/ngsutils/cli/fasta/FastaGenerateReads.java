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

@Command(name="fasta-genreads", desc="Generate mock reads from a reference FASTA file", category="fasta", experimental=true)
public class FastaGenerateReads extends AbstractOutputCommand {
    private String filename = null;
    private int readLength = 100;
    private int windowStep = 1;
    private int maxWildcard = 0;
    private char qualScore = 30+33;

    @Option(name="read-length", charName="l", desc="Read length", defaultValue="100")
    public void setReadLength(int readLength) {
        this.readLength = readLength;
    }

    @Option(name="step", charName="s", desc="Step (offset)", defaultValue="1")
    public void setWindowStep(int windowStep) {
        this.windowStep = windowStep;
    }

    @Option(name="wildcard", desc="Max number of wildcard bases (N)", defaultValue="0")
    public void setMaxWildcard(int maxWildcard) {
        this.maxWildcard = maxWildcard;
    }

    @Option(name="qual", charName="q", desc="Constant quality score (Phred scale)", defaultValue="30")
    public void setQualScore(int qualScore) {
        this.qualScore = (char)(qualScore + 33);
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
        
        if (windowStep > readLength) {
            throw new CommandArgumentException("Window-step must be smaller than or equal to read-length!");
        }
        
        final String[] current = new String[1];
        String qualString = "";
        for (int i=0; i<readLength; i++) {
            qualString += qualScore;
        }

        String buffer = "";
        int pos = 0;
        
        StringLineReader reader = new StringLineReader(filename);
        for (String line: IterUtils.wrap(reader.progress(new ProgressMessage<String>(){
                @Override
                public String msg(String s) {
                    return current[0];
                }
            }))) {
            if (line.charAt(0) == '>') {
                if (!buffer.equals("")) {
                    buffer = "";
                    pos = 0;
                }
                current[0] = line.substring(1).split("\\W",2)[0];
            } else {
                buffer += line;
                while (buffer.length() > readLength) {
                    String read = buffer.substring(0, readLength);
                    String qual = qualString;
                    
                    int count = 0;
                    if (read.indexOf('N')>-1 || read.indexOf('n') > -1) {
                        qual = "";
                        for (int i=0;i<readLength; i++) {
                            if (read.charAt(i) == 'N' || read.charAt(i) == 'n') {
                                qual+='#';
                                count ++;
                            } else {
                                qual += qualScore;
                            }
                        }
                    }
                    
                    if (count <= maxWildcard) {
                        out.write(("@"+current[0]+":"+pos+"-"+(pos+readLength)+"\n").getBytes());
                        out.write(read.getBytes());
                        out.write(("\n+\n"+qual+"\n").getBytes());
                    }

                    buffer = buffer.substring(windowStep);
                    pos += windowStep;
                }
            }
        }
        reader.close();
    }
}
