package io.compgen.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import htsjdk.samtools.SAMRecord;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

public class Whitelist extends AbstractBamFilter {
    final private Set<String> readNames = new HashSet<String>();
    
    public Whitelist(BamFilter parent, boolean verbose, String filename) throws FileNotFoundException, IOException {
        super(parent, verbose);
        for (String s: new StringLineReader(filename)) {
            readNames.add(StringUtils.strip(s));
        }
    }
    
    @Override
    public boolean keepRead(SAMRecord read) {
        if (readNames.contains(read.getReadName())) {
            return true;
        }
        return false;
    }
}
