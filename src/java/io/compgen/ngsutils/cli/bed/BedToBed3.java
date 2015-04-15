package io.compgen.ngsutils.cli.bed;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

import java.io.IOException;

@Command(name="bed-tobed3", desc="Convert a BED3+ file to a strict BED3 file", category="bed")
public class BedToBed3 extends AbstractOutputCommand {
    
    private String filename = null;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(filename))) {
            GenomeSpan coord = record.getCoord();
            new BedRecord(coord).write(out);
        }
    }
}
