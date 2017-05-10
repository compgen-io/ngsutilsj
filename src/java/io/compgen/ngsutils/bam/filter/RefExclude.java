package io.compgen.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import htsjdk.samtools.SAMRecord;

public class RefExclude extends AbstractBamFilter {
    final protected Set<String> invalidRefs = new HashSet<String>();

    
    public RefExclude(BamFilter parent, boolean verbose, String refs) throws FileNotFoundException, IOException {
        super(parent, verbose);
        for (String ref: refs.split(",")) {
            invalidRefs.add(ref);
        }
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        if (invalidRefs.contains(read.getReferenceName())) {
            return false;
        }
        return true;
    }
}
