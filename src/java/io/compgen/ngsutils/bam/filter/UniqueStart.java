package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class UniqueStart extends AbstractBamFilter {
    private int lastPlusPos = -1;
    private List<Integer> minusPos = new LinkedList<Integer>();
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
        } else if (read.getReferenceIndex() == lastRef && read.getReadNegativeStrandFlag()) {
            for (int i = minusPos.size()-1; i >= 0; i--) {
                if (read.getAlignmentEnd() > minusPos.get(i)) {
                    break;
                } else if (read.getAlignmentEnd() == minusPos.get(i)) {
                    return false;
                }
            }
        }

        lastRef = read.getReferenceIndex();

        while (minusPos.size()  > 0 && minusPos.get(0) <= read.getAlignmentStart()) {
            minusPos.remove(0);
        }
        
        if (read.getReadNegativeStrandFlag()) {
            minusPos.add(read.getAlignmentEnd());
            Collections.sort(minusPos);
        } else {
            lastPlusPos = read.getAlignmentStart();
        }

        return true;
    }
}
