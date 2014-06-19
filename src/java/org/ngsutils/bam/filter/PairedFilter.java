
package org.ngsutils.bam.filter;

import net.sf.samtools.SAMRecord;

/**
 * Makes sure reads are in reversed orientations and on the same chromosome. 
 */
public class PairedFilter extends AbstractBAMFilter {
    protected int flags = 0;
    
    public PairedFilter(BAMFilter parent, boolean verbose) {
        super(parent, verbose);
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        if (read.getReadUnmappedFlag()) {
            return false;
        }
        if (read.getMateUnmappedFlag()) {
            return false;
        }
        if (!read.getReferenceName().equals(read.getMateReferenceName())) {
            return false;
        }
        if (read.getReadNegativeStrandFlag() && read.getMateNegativeStrandFlag()) {
            return false;
        }
        if (!read.getReadNegativeStrandFlag() && !read.getMateNegativeStrandFlag()) {
            return false;
        }
        
        return true;
    }
}
