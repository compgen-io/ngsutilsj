package io.compgen.ngsutils.bam.filter;

import java.util.Map;

import htsjdk.samtools.SAMRecord;

public class TagMax extends AbstractBamFilter {
    
    final protected Map<String, Integer> tags;
    
    public TagMax(BamFilter parent, boolean verbose, Map<String, Integer> tags) {
        super(parent, verbose);
        this.tags = tags;
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        for (String tag: tags.keySet()) {
            Integer limit = tags.get(tag);
            Integer readval = null;

            if (tag.equals("MAPQ")) {
                readval = read.getMappingQuality();
            } else if (tag.equals("TLEN")) {
            	readval = Math.abs(read.getInferredInsertSize());
            } else if (read.getAttribute(tag) != null) { 
                readval = read.getIntegerAttribute(tag);
            }
            
            if (readval == null || readval > limit) {
                return false;
            }
        }
        return true;
    }
}
