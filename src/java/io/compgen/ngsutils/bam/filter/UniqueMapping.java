package io.compgen.ngsutils.bam.filter;

import io.compgen.ngsutils.bam.support.ReadUtils;
import htsjdk.samtools.SAMRecord;

public class UniqueMapping extends AbstractBamFilter {
    
    public UniqueMapping(BamFilter parent, boolean verbose) {
        super(parent, verbose);
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        return ReadUtils.isReadUniquelyMapped(read);
    }
}
