package org.ngsutils.bam.filter;

import java.util.Iterator;

import net.sf.samtools.SAMRecord;

public class NullFilter implements BamFilter {
    private Iterator<SAMRecord> iterator;
    private long total = 0;
    private long removed = 0;

    public NullFilter(Iterable<SAMRecord> reader) {
        this.iterator = reader.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public SAMRecord next() {
        this.total++;
        return iterator.next();
    }

    @Override
    public void remove() {
        iterator.remove();
    }

    @Override
    public Iterator<SAMRecord> iterator() {
        return iterator;
    }

    @Override
    public boolean keepRead(SAMRecord read) {
        return true;
    }

    public long getTotal() {
        return total;
    }

    public long getRemoved() {
        return removed;
    }

    @Override
    public BamFilter getParent() {
        return null;
    }

}
