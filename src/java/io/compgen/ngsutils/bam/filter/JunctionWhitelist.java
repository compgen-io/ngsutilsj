package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.support.ReadUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class JunctionWhitelist extends AbstractBamFilter {
    final private Set<GenomeSpan> junctions = new HashSet<GenomeSpan>();
    
    public JunctionWhitelist(BamFilter parent, boolean verbose, String filename) throws FileNotFoundException, IOException {
        super(parent, verbose);
        for (String s: new StringLineReader(filename)) {
            junctions.add(GenomeSpan.parse(StringUtils.strip(s)));
        }
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        // Only filter out reads that are junction spanning
        if (ReadUtils.isJunctionSpanning(read)) {
            for (GenomeSpan junc: ReadUtils.getJunctionsForRead(read, Orientation.UNSTRANDED)) {
                if (junctions.contains(junc)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
