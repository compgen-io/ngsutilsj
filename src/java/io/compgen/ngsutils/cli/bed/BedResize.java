package io.compgen.ngsutils.cli.bed;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

import java.io.IOException;

@Command(name="bed-resize", desc="Resize BED regions (extend or shrink)", category="bed")
public class BedResize extends AbstractOutputCommand {
    
    private String filename = null;
    private int len5 = 0;
    private int len3 = 0;
    private int maxLen = -1;
    
    @Option(charName="5", desc="Extend a region in the 5' direction (strand-specific, negative to shrink)")
    public void setLen5(int len5) {
        this.len5 = len5;
    }

    @Option(charName="3", desc="Extend a region in the 3' direction (strand-specific, negative to shrink)")
    public void setLen3(int len3) {
        this.len3 = len3;
    }

    @Option(name="max", desc="Maximum length to expand (regions above this length will not be expanded, optional)")
    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
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
        
        if (len5 == 0 && len3 == 0) {
            throw new CommandArgumentException("You must set either -5 or -3 (or both)!");
        }
        
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(filename))) {
            GenomeSpan coord = record.getCoord();
            
            if (maxLen > 0 && coord.size() >= maxLen) {
                record.write(out);
                continue;
            }
            
            coord = coord.extend5(len5);
            coord = coord.extend3(len3);
            
            new BedRecord(coord, record.getName(), record.getScore(), record.getExtras()).write(out);
        }
    }
}
