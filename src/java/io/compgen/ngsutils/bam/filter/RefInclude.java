package io.compgen.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;

import htsjdk.samtools.SAMRecord;

public class RefInclude extends RefExclude {
    
    public RefInclude(BamFilter parent, boolean verbose, String refs) throws FileNotFoundException, IOException {
        super(parent, verbose, refs);
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        return !super.keepRead(read);
    }
}
