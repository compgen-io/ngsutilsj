package io.compgen.ngsutils.support;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

public abstract class AbstractLineReader<T> implements Iterable<T> {
    final private Reader reader;
    final protected FileChannel channel;
    public AbstractLineReader(String filename) throws IOException {
        if (filename.equals("-")) {
            this.reader = new InputStreamReader(System.in);
            this.channel = null;
        } else {
            FileInputStream fis = new FileInputStream(filename);
            this.channel = fis.getChannel();
            if (filename.endsWith(".gz")) {
                this.reader = new InputStreamReader(new GZIPInputStream(fis));
            } else {
                this.reader = new InputStreamReader(fis);
            }
        }
    }

    public AbstractLineReader(InputStream is) {
        this(is, null);
    }
    
    public AbstractLineReader(InputStream is, FileChannel channel) {
        this.reader = new InputStreamReader(is);
        this.channel = channel;
    }
    
    public void close() throws IOException {
        this.reader.close();
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
                T out = null;
                while (out == null) {
                    out = convertLine(next);
                    next = readnext();     
                }
                return out;
            }

            @Override
            public void remove() {
            }
            
        };
    }
}
