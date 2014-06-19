package org.ngsutils.bam.filter;

import java.util.Iterator;

import net.sf.samtools.SAMRecord;

public class SAMRecordIterable implements Iterable<SAMRecord> {
    private Iterator<SAMRecord> it;
    public SAMRecordIterable(Iterator<SAMRecord> it) {
        this.it = it;
    }
    @Override
    public Iterator<SAMRecord> iterator() {
        return it;
    }

}
