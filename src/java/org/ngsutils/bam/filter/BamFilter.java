package org.ngsutils.bam.filter;

import java.util.Iterator;

import net.sf.samtools.SAMRecord;

public interface BamFilter extends Iterator<SAMRecord>, Iterable<SAMRecord> {
    public abstract boolean keepRead(SAMRecord read);
    public abstract long getTotal();
    public abstract long getRemoved();
    public BamFilter getParent();
}