package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMRecord;

import java.util.Iterator;

public interface BamFilter extends Iterator<SAMRecord>, Iterable<SAMRecord> {
    public abstract boolean keepRead(SAMRecord read);
    public abstract long getTotal();
    public abstract long getRemoved();
    public BamFilter getParent();
    public SAMFileWriter getFailedWriter();
    public abstract void setPairedKeep();
    public abstract void setPairedRemove();
}
