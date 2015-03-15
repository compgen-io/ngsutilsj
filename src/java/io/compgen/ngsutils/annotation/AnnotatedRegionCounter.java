package io.compgen.ngsutils.annotation;

import htsjdk.samtools.SAMRecord;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AnnotatedRegionCounter {
    protected final GTFAnnotationSource gtf;
    protected Map<GenicRegion, Long> regionCounts = new HashMap<GenicRegion, Long>();
    
    public AnnotatedRegionCounter(String filename) throws NumberFormatException, IOException {
        System.err.print("Loading GTF annotation: "+filename+"...");
        gtf = new GTFAnnotationSource(filename);
        System.err.println(" done");
        for (GenicRegion region: GenicRegion.values()) {
            regionCounts.put(region, 0l);
        }
    }
    
    public void addRead(SAMRecord read) {
        GenicRegion reg = gtf.findGenicRegion(read);
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
