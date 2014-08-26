package org.ngsutils.bam.filter;

import net.sf.samtools.SAMRecord;

public class FilterFlags extends AbstractBamFilter {
    protected int flags = 0;
    
    public FilterFlags(BamFilter parent, boolean verbose, int flags) {
        super(parent, verbose);
    }

    public void addFlag(int flag) {
        this.flags |= flag;
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        return ((read.getFlags() & flags) == 0);
    }
}
