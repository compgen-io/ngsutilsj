package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.FastaRecord;

import java.io.IOException;

@Command(name="fasta-tag", desc="Add prefix/suffix to FASTA sequence names", category="fasta")
public class FastaTag extends AbstractOutputCommand {
    
    private String filename = null;
    private String prefix = null;
    private String suffix = null;
    private int wrap = -1;
    
    
    @Option(name="prefix", charName="p", desc="Add a prefix to the sequence name")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    @Option(name="suffix", charName="s", desc="Add a prefix to the sequence name")
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
    @Option(name="wrap", desc="Wrap the sequence to be length N", defaultValue="-1")
    public void setWrap(int wrap) {
        this.wrap = wrap;
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
        
        if (prefix == null && suffix == null) {
            throw new CommandArgumentException("You must set either prefix or suffix (or both)!");
        }
        
        FastaReader fasta = FastaReader.open(filename);
        for (FastaRecord record: IterUtils.wrap(fasta.iterator())) {
            String newname = "";
            if (prefix!=null) {
                newname += prefix;
            }
            newname += record.name;
            if (suffix!=null) {
                newname += suffix;
            }
            
            FastaRecord newRecord = new FastaRecord(newname, record.seq, record.comment);
            newRecord.write(out, wrap);
        }
        
        fasta.close();        
    }
}
