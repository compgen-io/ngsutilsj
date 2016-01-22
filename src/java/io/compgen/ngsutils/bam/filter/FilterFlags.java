package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

public class FilterFlags extends AbstractBamFilter {
    protected int flags = 0;
    
    public FilterFlags(BamFilter parent, boolean verbose, int flags) {
        super(parent, verbose);
        this.flags = flags;
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        return ((read.getFlags() & flags) == 0);
    }
}
