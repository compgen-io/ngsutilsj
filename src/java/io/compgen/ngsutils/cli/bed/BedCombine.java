package io.compgen.ngsutils.cli.bed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

@Command(name="bed-combine", desc="Given reference FASTA (indexed) and two or more BED files, combine the BED annotations into one output BED file", category="bed")
public class BedCombine extends AbstractOutputCommand {
    
    private String refFilename = null;
    private List<String> bedFilenames = null;
    
    private String defval = null;
    
    private boolean ignoreStrand = false;
    private boolean single = false;
    
    @UnnamedArg(name = "REF QUERY1 ... ")
    public void setFilenames(String[] filenames) throws CommandArgumentException {
        if (filenames.length < 3) {
            throw new CommandArgumentException("Invalid ref and query values.");
        }
        this.refFilename = filenames[0];
        this.bedFilenames = new ArrayList<String>();
        for (int i=1; i<filenames.length; i++) {
        	this.bedFilenames.add(filenames[i]);
        }
    }
    
    @Option(name="single", desc="Use only one annotation for each position (prioritzed based on arg order)")
    public void setSingle(boolean val) {
        this.single = val;
    }

    @Option(name="default", desc="Default annotation for regions that are unannotated")
    public void setDefault(String defval) {
        this.defval = defval;
    }

    @Option(name="ns", desc="Ignore strand")
    public void setIgnoreStrand(boolean val) {
        this.ignoreStrand = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (refFilename == null || bedFilenames == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        File fai = new File(refFilename + ".fai");
        if (!fai.exists()) {
            throw new CommandArgumentException("FASTA reference must be indexed!");
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
