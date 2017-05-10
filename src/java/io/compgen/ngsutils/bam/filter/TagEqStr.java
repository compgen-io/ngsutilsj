package io.compgen.ngsutils.bam.filter;

import java.util.Map;

import htsjdk.samtools.SAMRecord;

public class TagEqStr extends AbstractBamFilter {
    
    final protected Map<String, String> tags;
    
    public TagEqStr(BamFilter parent, boolean verbose, Map<String, String> tags) {
        super(parent, verbose);
        this.tags = tags;
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        for (String tag: tags.keySet()) {
            String expected = tags.get(tag);
            String readval = null;

            if (read.getAttribute(tag) != null) { 
                readval = read.getStringAttribute(tag);
            }
            
            if (readval == null || !readval.equals(expected)) {
                return false;
            }
        }
        return true;
    }
}
