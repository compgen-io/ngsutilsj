package org.ngsutils.cli.bam.count;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.bam.Strand;
import org.ngsutils.support.AbstractLineReader;

public class BedSpans extends AbstractLineReader<Span> implements SpanSource {
    private int numCols;
    
    public BedSpans(String filename) throws IOException {
        super(filename);

        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line = br.readLine();
        String[] cols = line.split("\\t", -1);
        numCols = cols.length;
        br.close();
    }

    public Span convertLine(String line) {
        String[] cols = line.split("\\t", -1);
        Span span;
        if (cols.length > 3) {
            span = new Span(cols[0], Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), Strand.parse(cols[5]), cols);
        } else {
            span = new Span(cols[0], Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), Strand.NONE, cols);
        }
        return span;
    }


    @Override
    public String[] getHeader() {
        String[] tmpl = new String[]{"chrom", "start", "end", "name", "score", "strand"};

        List<String> out = new ArrayList<String>();
       
        
        for (int i=0; i< numCols; i++) {
            if (i < tmpl.length) {
                out.add(tmpl[i]);
            } else {
                out.add("col"+(i+1));
            }
        }

        String[] target = new String[out.size()];
        out.toArray(target);
        return target;
    }
}
