package io.compgen.ngsutils.cli.bam.count;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import htsjdk.samtools.SAMRecord;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.ReadUtils;

public class BinCounter {
    public interface BinCounterExporter {
        public void writeBin(String ref, int start, int end, Strand strand, int count);
    }
    
    private SortedMap<Integer, Integer> plus = new TreeMap<Integer, Integer>();
    private SortedMap<Integer, Integer> minus = new TreeMap<Integer, Integer>();

    private final Orientation orient;
    private final int binSize;
    private final boolean stranded;
    private final boolean includeEmpty;
    
    private BinCounterExporter callback;
    
    private int curBin = 0;
    
    private String refName = null;
    private int refId = -1;
    private int refLength = -1;

    public BinCounter(Orientation orient, int binSize, boolean stranded, boolean includeEmpty, BinCounterExporter callback) {
        this.orient = orient;
        this.binSize = binSize;
        this.stranded = stranded;
        this.includeEmpty = includeEmpty;
        this.callback = callback;
    }

    public void setCurrentReference(String name, int tid, int length) {
        if (this.refName != null) {
            flush();
        }

//        System.err.println("New reference: "+name);
        this.refName = name;
        this.refId = tid;
        this.refLength = length;
        this.curBin = 0;
    }
    
    public void flush() {
//        System.err.println("Flushing reference: "+refName);
        // flush any remaining counts, add padding if needed
        while ((curBin * binSize) < refLength) {
            int count = 0;
            if (plus.containsKey(curBin)) {
                count = plus.get(curBin);
                plus.remove(curBin);
            }
            if (includeEmpty || count > 0) {                
                int binEnd = Math.min((curBin + 1) * binSize, refLength);
                callback.writeBin(refName, curBin * binSize, binEnd, Strand.PLUS, count);
            }
            count = 0;
            if (stranded) {
                if (minus.containsKey(curBin)) {
                    count = minus.get(curBin);
                    minus.remove(curBin);
                }
                if (includeEmpty || count > 0) {
                    int binEnd = Math.min((curBin + 1) * binSize, refLength);
                    callback.writeBin(refName, curBin * binSize, binEnd, Strand.MINUS, count);
                }
            }
            curBin++;
        }
    }

    public void addRead(SAMRecord read) {
        if (read.getReferenceIndex() != this.refId) {
            setCurrentReference(read.getReferenceName(), read.getReferenceIndex(), read.getHeader().getSequence(read.getReferenceIndex()).getSequenceLength());
        }

        Strand strand = ReadUtils.getFragmentEffectiveStrand(read, orient);

        int startbin = read.getAlignmentStart() / binSize;
        int endbin = read.getAlignmentEnd() / binSize;
        
        // catch up to the current bin.
        while (curBin < startbin) {
            int count = 0;
            if (plus.containsKey(curBin)) {
                count = plus.get(curBin);
                plus.remove(curBin);
            }
            if (includeEmpty || count > 0) {
                int binEnd = Math.min((curBin + 1) * binSize, refLength);
                callback.writeBin(refName, curBin * binSize, binEnd, Strand.PLUS, count);
            }
            if (stranded) {
                count = 0;
                if (minus.containsKey(curBin)) {
                    count = minus.get(curBin);
                    minus.remove(curBin);
                }
                if (includeEmpty || count > 0) {
                    int binEnd = Math.min((curBin + 1) * binSize, refLength);
                    callback.writeBin(refName, curBin * binSize, binEnd, Strand.MINUS, count);
                }
            }
            curBin++;
        }
        
        List<Integer> removeme = new ArrayList<Integer>();
        for (int bin: plus.keySet()) {
            if (bin < curBin) {
                int count = 0;
                if (plus.containsKey(bin)) {
                    count = plus.get(bin);
                    removeme.add(bin);
//                    System.err.println("flushing plus bin: "+bin);
                }
                if (includeEmpty || count > 0) {
                    int binEnd = Math.min((curBin + 1) * binSize, refLength);
                    callback.writeBin(refName, bin * binSize, binEnd, Strand.PLUS, count);
                }
            }
        }
        for (int bin: removeme) {
            plus.remove(bin);
        }
        
        removeme.clear();
        if (stranded) {
            for (int bin: minus.keySet()) {
                if (bin < curBin) {
                    int count = 0;
                    if (minus.containsKey(bin)) {
                        count = minus.get(bin);
                        removeme.add(bin);
    //                    System.err.println("flushing minus bin: "+bin);
                    }
                    if (includeEmpty || count > 0) {
                        int binEnd = Math.min((curBin + 1) * binSize, refLength);
                        callback.writeBin(refName, bin * binSize, binEnd, Strand.MINUS, count);
                    }
                }
            }
            for (int bin: removeme) {
                minus.remove(bin);
            }
        }
        
        if (!stranded || strand == Strand.PLUS) {
            if (!plus.containsKey(startbin)) {
                plus.put(startbin,  1);
            } else {
                plus.put(startbin,  plus.get(startbin)+1);
            }
        } else {
            if (!minus.containsKey(endbin)) {
                minus.put(endbin,  1);
            } else {
                minus.put(endbin,  minus.get(endbin)+1);
            }
        }
    }
}
