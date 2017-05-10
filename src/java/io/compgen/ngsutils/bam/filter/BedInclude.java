package io.compgen.ngsutils.bam.filter;

import java.io.FileNotFoundException;
import java.io.IOException;

import htsjdk.samtools.SAMRecord;
import io.compgen.ngsutils.bam.Orientation;

public class BedInclude extends BedExclude {
    public BedInclude(BamFilter parent, boolean verbose, String filename, Orientation orient) throws FileNotFoundException, IOException {
        super(parent, verbose, filename, orient);
    }
    @Override
    public boolean keepRead(SAMRecord read) {
        return !super.keepRead(read);
    }
}
