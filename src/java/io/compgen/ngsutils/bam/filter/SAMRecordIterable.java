package io.compgen.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

import java.util.Iterator;

public class SAMRecordIterable implements Iterable<SAMRecord> {
    private Iterator<SAMRecord> it;
    public SAMRecordIterable(Iterator<SAMRecord> it) {
        this.it = it;
    }
    @Override
    public Iterator<SAMRecord> iterator() {
        return it;
    }
    
//    public static Iterator<SAMRecord> wrap(final SAMRecordIterator it) {
//        return new Iterator<SAMRecord>() {
//
//            @Override
//            public boolean hasNext() {
//                if (!it.hasNext()) {
//                    it.close();
//                    return false;
//                }
//                return true;
//            }
//
//            @Override
//            public SAMRecord next() {
//                return it.next();
//            }
//
//            @Override
//            public void remove() {
//                it.remove();
//            }};
//    }
//    
}
