package org.ngsutils.support;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

public abstract class AbstractLineReader<T> implements Iterable<T> {
    final private Reader reader;
    public AbstractLineReader(String filename) throws IOException {
        if (filename.equals("-")) {
            this.reader = new InputStreamReader(System.in);
        } else if (filename.endsWith(".gz")) {
            this.reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)));
        } else {
            this.reader = new FileReader(filename);
        }
    }
    public AbstractLineReader(InputStream is) {
        this.reader = new InputStreamReader(is);
    }
    
    protected abstract T convertLine(String line);
    
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            BufferedReader br = new BufferedReader(reader);
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
            public T next() {
                String cur = next;
                next = readnext();               
                return convertLine(cur);
            }

            @Override
            public void remove() {
            }
            
        };
    }
}
