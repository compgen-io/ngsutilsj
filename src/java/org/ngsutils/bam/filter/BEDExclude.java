package org.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;

import net.sf.samtools.SAMRecord;

import org.ngsutils.annotation.BEDAnnotationSource;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Orientation;

public class BEDExclude extends AbstractBAMFilter {
    final protected BEDAnnotationSource bed;
    final protected Orientation orient;
    protected boolean onlyWithin = false;
    
    public BEDExclude(BAMFilter parent, boolean verbose, String filename, Orientation orient) throws FileNotFoundException, IOException {
        super(parent, verbose);
        this.bed = new BEDAnnotationSource(filename);
        this.orient = orient;
    }
    
    public void setOnlyWithin(boolean val) {
        this.onlyWithin = val;
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        if (onlyWithin) {
            for (GenomeRegion block: GenomeRegion.getReadAlignmentRegions(read, orient)) {
                if (!bed.hasAnnotation(block, onlyWithin)) {
                    return true;
                }
            }
            if (verbose) {
                System.err.println("All blocks matched BED region (s)!");
            }
            return false;
            
        }
        for (GenomeRegion block: GenomeRegion.getReadAlignmentRegions(read, orient)) {
            if (bed.hasAnnotation(block)) {
                if (verbose) {
                    System.err.println("Block: "+block+ " matched BED region!");
                }
                return false;
            }
        }
        return true;
    }
}
