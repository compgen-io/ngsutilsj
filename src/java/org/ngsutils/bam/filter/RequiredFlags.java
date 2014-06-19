package org.ngsutils.bam.filter;

import net.sf.samtools.SAMRecord;

public class RequiredFlags extends AbstractBAMFilter {
    protected int flags = 0;
    
    public RequiredFlags(BAMFilter parent, boolean verbose, int flags) {
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
