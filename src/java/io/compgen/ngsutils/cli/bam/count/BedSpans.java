package io.compgen.ngsutils.cli.bam.count;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.AbstractLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.bam.Strand;

public class BedSpans extends AbstractLineReader<SpanGroup> implements SpanSource {
    private int numCols;
    
    public BedSpans(String filename) throws IOException {
        super(filename);

        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line = br.readLine();
        String[] cols = line.split("\\t", -1);
        numCols = cols.length;
        br.close();
    }
    
    public long position() {
        if (channel!=null) {
            try {
                return channel.position();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
    public long size() {
        if (channel!=null) {
            try {
                return channel.size();
            } catch (IOException e) {
            }
        }
        return -1;
    }
    
    public SpanGroup convertLine(String line) {
        if (line == null || line.trim().equals("")) {
            return null;
        }
        line = StringUtils.rstrip(line);
        String[] cols = line.split("\\t", -1);
        SpanGroup span;
        
        if (cols.length < 3) {
            return null;
        }
        
        if (cols.length > 5) {
            span = new SpanGroup(cols[0], Strand.parse(cols[5]), cols, Integer.parseInt(cols[1]), Integer.parseInt(cols[2]));
        } else {
            span = new SpanGroup(cols[0], Strand.NONE, cols, Integer.parseInt(cols[1]), Integer.parseInt(cols[2]));
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
