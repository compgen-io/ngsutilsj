package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;
import io.compgen.ngsutils.annotation.BedAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;

import java.io.FileNotFoundException;
import java.io.IOException;

public class BedExclude extends AbstractBamFilter {
    final protected BedAnnotationSource bed;
    final protected Orientation orient;
    protected boolean onlyWithin = false;
    protected boolean startPos = false;
    
    public BedExclude(BamFilter parent, boolean verbose, String filename, Orientation orient) throws FileNotFoundException, IOException {
        super(parent, verbose);
        this.bed = new BedAnnotationSource(filename);
        this.orient = orient;
    }
    
    public void setOnlyWithin(boolean val) {
        this.onlyWithin = val;
    }
    
    public void setReadStartPos(boolean val) {
        this.startPos = val;
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        if (startPos) {
            GenomeSpan start = GenomeSpan.getReadStartPos(read, orient);
            if (!bed.hasAnnotation(start, onlyWithin)) {
                return true;
            }
            return false;
        }
        
        if (onlyWithin) {
            for (GenomeSpan block: GenomeSpan.getReadAlignmentRegions(read, orient)) {
                if (!bed.hasAnnotation(block, onlyWithin)) {
                    return true;
                }
            }
            if (verbose) {
                System.err.println("All blocks matched BED region (s)!");
            }
            return false;
            
        }
        for (GenomeSpan block: GenomeSpan.getReadAlignmentRegions(read, orient)) {
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
