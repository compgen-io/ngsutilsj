package io.compgen.ngsutils.annotation;

import java.util.List;
import java.io.IOException;

import htsjdk.samtools.SAMRecord;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bed.BedRecord;

public class BedRegionCounter {
    protected final BedAnnotationSource bed;
    protected long total = 0;
    protected long ontarget = 0;
    protected long size = 0;
    
    public BedRegionCounter(String filename) throws NumberFormatException, IOException {
        System.err.print("Loading BED annotation: "+filename+"...");
        bed = new BedAnnotationSource(filename);
        System.err.print(" calc size...");
        
        for (GenomeSpan span: IterUtils.wrap(bed.regionsIterator())) {
            size += (span.end-span.start);
        }
        
        System.err.println(" done ("+size+" bp)");
    }
    
    public void addRead(SAMRecord read, Orientation orient) {
        total++;
        for (GenomeSpan reg: GenomeSpan.getReadAlignmentRegions(read, orient)) {
            List<BedRecord> foo = bed.findAnnotation(reg);
            if (foo != null && foo.size() > 0) {
                ontarget++;
                return;
            }
        }
    }

    public long getTotalCount() {
        return total;
    }

    public long getOnTarget() {
        return ontarget;
    }

    public long getBedSize() {
        return size;
    }
}
