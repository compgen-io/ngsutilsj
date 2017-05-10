package io.compgen.ngsutils.bam.filter;

import java.util.Iterator;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMRecord;

public class NullFilter implements BamFilter {
    private Iterator<SAMRecord> iterator;
    private long total = 0;
    private long removed = 0;
    
    private SAMFileWriter failedWriter;

    public NullFilter(Iterator<SAMRecord> iterator, SAMFileWriter failedWriter) {
        this.iterator = iterator;
        this.failedWriter = failedWriter;
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

    @Override
    public SAMFileWriter getFailedWriter() {
        return failedWriter;
    }

    @Override
    public void setPairedKeep() {
        // NOOP        
    }

    @Override
    public void setPairedRemove() {
        // NOOP        
    }

}
