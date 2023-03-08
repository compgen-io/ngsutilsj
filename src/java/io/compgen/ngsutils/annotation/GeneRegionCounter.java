package io.compgen.ngsutils.annotation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import htsjdk.samtools.SAMRecord;
import io.compgen.ngsutils.bam.Orientation;

public class GeneRegionCounter {
    protected final GTFAnnotationSource gtf;
    protected Map<GenicRegion, Long> regionCounts = new HashMap<GenicRegion, Long>();
    
    public GeneRegionCounter(String filename) throws NumberFormatException, IOException {
        System.err.print("Loading GTF annotation: "+filename+"...");
        gtf = new GTFAnnotationSource(filename, null);
        System.err.println(" done");
        for (GenicRegion region: GenicRegion.values()) {
            regionCounts.put(region, 0L);
        }
    }
    
    public void addRead(SAMRecord read, Orientation orient) {
        GenicRegion reg = gtf.findGenicRegion(read, orient);
        regionCounts.put(reg, regionCounts.get(reg) + 1);
        
    }

    public long getRegionCount(GenicRegion reg) {
        return regionCounts.get(reg);
    }

    public long getRegionCount(GenicRegion... regs) {
        long acc = 0;
        for (GenicRegion reg: regs) {
            acc += regionCounts.get(reg);
        }
        return acc;
    }
}
