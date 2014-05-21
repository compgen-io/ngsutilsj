package org.ngsutils.cli.bam.count;

import org.ngsutils.bam.Strand;

public class Span {
    final private String refName;
    final private int[] starts;  // 0-based!
    final private int[] ends;
    final private Strand strand;
    final private String[] fields;
    
    public Span(String refName, int[] starts, int[] ends, Strand strand, String[] fields) {
        this.refName = refName;
        this.starts = starts;
        this.ends = ends;
        this.strand = strand;
        this.fields = fields;
    }

    public Span(String refName, int start, int end, Strand strand, String[] fields) {
        this.refName = refName;
        this.starts = new int[] { start };
        this.ends = new int[] { end };
        this.strand = strand;
        this.fields = fields;
    }

    public String getRefName() {
        return refName;
    }

    public int[] getStarts() {
        return starts;
    }

    public int[] getEnds() {
        return ends;
    }
    
    public Strand getStrand() {
        return strand;
    }
    
    public String[] getFields() {
        return fields;
    }

    public boolean within(String ref, int pos) {
        if (refName.equals(ref)) {
            for (int i=0; i < starts.length; i++) {
                if (starts[i] <= pos && pos <= ends[i]) {
                    return true;
                }
            }
        }
        return false;
    }
}
