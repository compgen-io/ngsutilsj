package io.compgen.ngsutils.cli.bam.count;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;

import java.util.Iterator;

public class BinSpans implements SpanSource {
    final private SAMSequenceDictionary seqdict;
    final private int binsize;
    final private Orientation orient;
    private long pos = 0;
    
    public BinSpans(SAMSequenceDictionary seqdict, int binsize, Orientation orient) {
        this.seqdict = seqdict;
        this.binsize = binsize;
        this.orient = orient;
    }

    @Override
    public Iterator<SpanGroup> iterator() {
        pos = 0;
        return new Iterator<SpanGroup>() {
            int currentSeq = 0;
            int currentPos = 0;
            SpanGroup next = null;
            @Override
            public boolean hasNext() {
                if (currentSeq < seqdict.size()) {
                    return true;
                }                
                return false;
            }

            @Override
            public SpanGroup next() {
                pos ++;
                if (next != null) {
                    SpanGroup tmp = next;
                    next = null;
                    return tmp;
                }
                int start = currentPos;
                int end = currentPos + binsize;
                String ref = seqdict.getSequence(currentSeq).getSequenceName();
                
                if (end >= seqdict.getSequence(currentSeq).getSequenceLength()) {
                    end = seqdict.getSequence(currentSeq).getSequenceLength();
                    currentSeq++;
                    currentPos = 0;
                } else {
                    currentPos = end;
                }
                
                if (orient == Orientation.UNSTRANDED) {
                    return new SpanGroup(ref, Strand.NONE, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.NONE.toString()}, start, end);
                } else {
                    next = new SpanGroup(ref, Strand.MINUS, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.MINUS.toString()}, start, end);
                    return new SpanGroup(ref, Strand.PLUS, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.PLUS.toString()}, start, end);
                }
            }

            @Override
            public void remove() {
            }
            
        };
    }

    
    
    public long size() {
        int acc = 0;
        for (SAMSequenceRecord seq: seqdict.getSequences()) { 
            acc += (seq.getSequenceLength() / binsize) + 1;
        }
        
        return acc;
    }
    
    @Override
    public String[] getHeader() {
        return new String[]{"chrom", "start", "end", "strand"};
    }

    @Override
    public long position() {
        return pos;
    }
}
