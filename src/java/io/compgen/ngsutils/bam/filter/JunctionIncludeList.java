package io.compgen.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import htsjdk.samtools.SAMRecord;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.ReadUtils;

public class JunctionIncludeList extends AbstractBamFilter {
    final private Set<String> junctions = new HashSet<String>();
    
    public JunctionIncludeList(BamFilter parent, boolean verbose, String filename) throws FileNotFoundException, IOException {
        super(parent, verbose);
        for (String s: new StringLineReader(filename)) {
            junctions.add(StringUtils.strip(s));
        }
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        // Only filter out reads that are junction spanning
        if (ReadUtils.isJunctionSpanning(read)) {
            for (GenomeSpan junc: ReadUtils.getJunctionsForRead(read, Orientation.UNSTRANDED)) {
//                System.out.println(read.getReadName() + " " + read.getReferenceName()+ " " + (read.getAlignmentStart()-1) +" " + read.getCigarString()+" " +junc.toString() + " " + (junctions.contains(junc.toString()) ? "PASS": "FAIL"));
                if (!junctions.contains(junc.clone(Strand.NONE).toString())) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }
}
