package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

public class RequiredFlags extends AbstractBamFilter {
    protected int flags = 0;
    
    public RequiredFlags(BamFilter parent, boolean verbose, int flags) {
        super(parent, verbose);
    }

    public void addFlag(int flag) {
        this.flags |= flag;
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        return ((read.getFlags() & flags) == flags);
    }
}
