package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

public class UniqueMapping extends AbstractBamFilter {
    
    public UniqueMapping(BamFilter parent, boolean verbose) {
        super(parent, verbose);
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        Integer mappings = read.getIntegerAttribute("NH");
        if (mappings == null || mappings < 0) {
            mappings = read.getIntegerAttribute("IH");
        }
        if (mappings != null && mappings > 1) {
            return false;
        }
        return true;
    }
}
