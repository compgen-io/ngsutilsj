package org.ngsutils.cli.bam.count;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringUtils;

public class BEDSpans implements SpanSource {
    final private String filename;
    private int numCols;
    
    public BEDSpans(String filename) throws IOException {
        this.filename=filename;
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line = br.readLine();
        String[] cols = line.split("\\t", -1);
        numCols = cols.length;
        br.close();
    }

    @Override
    public Iterator<Span> iterator() {
        try {
            return new Iterator<Span>() {
                BufferedReader br = new BufferedReader(new FileReader(filename));
                String next = readnext();
                
                private String readnext() {
                    String line = null;
                    
                    while (line == null) {
                        try {
                            line = br.readLine();
                        } catch (IOException e) {
                            line = null;
                        }
                        if (line == null) {
                            break;
                        }
                        
                        line = StringUtils.strip(line);
                        if (line.length() == 0) {
                            line = null;
                        }
                    }
                    if (line == null) {
                        try {
                            br.close();
                        } catch (IOException e) {
                        }
                    }
                    return line;
                }
                
                @Override
                public boolean hasNext() {
                    return (next != null);
                }

                @Override
                public Span next() {
                    String[] cols = next.split("\\t", -1);
                    Span span;
                    if (cols.length > 3) {
                        span = new Span(cols[0], Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), Strand.parse(cols[5]), cols);
                    } else {
                        span = new Span(cols[0], Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), Strand.NONE, cols);
                    }
                    next = readnext();               
                    return span;
                }

                @Override
                public void remove() {
                }
                
            };
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
