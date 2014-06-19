package org.ngsutils.bam.filter;

import java.util.Iterator;

import net.sf.samtools.SAMRecord;

public interface BAMFilter extends Iterator<SAMRecord>, Iterable<SAMRecord> {
    public abstract boolean keepRead(SAMRecord read);
    public abstract long getTotal();
    public abstract long getRemoved();
    public BAMFilter getParent();
}