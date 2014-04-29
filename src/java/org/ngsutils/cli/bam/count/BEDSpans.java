package org.ngsutils.cli.bam.count;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringUtils;

public class BEDSpans implements Iterable<Span> {
    final private String filename;
    
    public BEDSpans(String filename) {
        this.filename=filename;
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
}
