package io.compgen.ngsutils.cli.bam.count;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;


public class SpanGroup implements Iterable<GenomeSpan> {
    final private String refName;
    final private Strand strand;
    final private String[] fields;

    protected List<GenomeSpan> spans = new ArrayList<GenomeSpan>();
    
    public SpanGroup(String refName, Strand strand, String[] fields, int start, int end) {
        this.refName = refName;
        this.strand = strand;
        this.fields = fields;
        
        addSpan(start, end);
    }

    
    public SpanGroup(String refName, Strand strand, String[] fields) {
        this.refName = refName;
        this.strand = strand;
        this.fields = fields;
    }

    public void addSpan(int start, int end) {
        spans.add(new GenomeSpan(refName, start, end, strand));
    }
    
    public String getRefName() {
        return refName;
    }

    public int getStart() {
        int min = spans.get(0).start;
        for (int i=1; i<spans.size(); i++) {
            if (spans.get(i).start < min) {
                min = spans.get(i).start;
            }
        }
        return min;
    }

    public int getEnd() {
        int max = spans.get(0).end;
        for (int i=1; i<spans.size(); i++) {
            if (spans.get(i).end > max) {
                max = spans.get(i).end;
            }
        }
        return max;
    }
    
    public Strand getStrand() {
        return strand;
    }
    
    public String[] getFields() {
        return fields;
    }
    
    public int size() {
        return spans.size();
    }
    
    public GenomeSpan get(int i) {
        return spans.get(i);
    }

    @Override
    public Iterator<GenomeSpan> iterator() {
        return spans.iterator();
    }
}
