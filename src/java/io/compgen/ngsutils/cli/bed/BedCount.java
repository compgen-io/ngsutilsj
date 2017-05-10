package io.compgen.ngsutils.cli.bed;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.BedCountAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeAnnotation;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.bed.BedRecordCount;

@Command(name="bed-count", desc="Given reference and query BED files, count the number of query regions that are contained within each reference region", category="bed")
public class BedCount extends AbstractOutputCommand {
    
    private String refFilename = null;
    private String queryFilename = null;
    
    private boolean ignoreStrand = false;
    
    @UnnamedArg(name = "REF QUERY")
    public void setFilenames(String[] filenames) throws CommandArgumentException {
        if (filenames.length != 2) {
            throw new CommandArgumentException("Invalid target/query");
        }
        this.refFilename = filenames[0];
        this.queryFilename = filenames[1];
    }
    
    @Option(name="ns", desc="Ignore strand")
    public void setIgnoreStrand(boolean val) {
        this.ignoreStrand = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (refFilename == null || queryFilename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        BedCountAnnotationSource ann = new BedCountAnnotationSource(refFilename);
        
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(queryFilename))) {
            GenomeSpan coord = record.getCoord();
            if (ignoreStrand) {
                coord = coord.clone(Strand.NONE);
            }
            for (BedRecordCount rec: ann.findAnnotation(coord)) {
                rec.incr();
            }
        }
        
        for (GenomeAnnotation<BedRecordCount> ga: IterUtils.wrap(ann.iterator())) {
            ga.getValue().write(out);
        }
    }
}
