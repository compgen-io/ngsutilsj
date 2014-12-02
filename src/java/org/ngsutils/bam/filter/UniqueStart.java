package org.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

public class UniqueStart extends AbstractBamFilter {
    private int lastPlusPos = -1;
    private int lastMinusPos = -1;
    private int lastRef = -1;
    public UniqueStart(BamFilter parent, boolean verbose) {
        super(parent, verbose);
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        if (read.getReadUnmappedFlag()) {
            return false;
        }
        
        if (read.getReferenceIndex() == lastRef && !read.getReadNegativeStrandFlag() && read.getAlignmentStart() == lastPlusPos) {
            return false;
        } else if (read.getReferenceIndex() == lastRef && read.getReadNegativeStrandFlag() && read.getAlignmentEnd() == lastMinusPos) {
            return false;
        }

        lastRef = read.getReferenceIndex();
        if (read.getReadNegativeStrandFlag()) {
            lastMinusPos = read.getAlignmentEnd();
        } else {
            lastPlusPos = read.getAlignmentStart();
        }

        return true;
    }
}
