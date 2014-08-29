package org.ngsutils.sqz;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.InflaterInputStream;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataInput;
import org.ngsutils.support.io.SHA1InputStream;

public abstract class SQZReader implements Iterable<FastqRead>{
    public abstract FastqRead nextRead() throws IOException;
    public abstract FastqRead[] nextPair() throws IOException;

    protected boolean ignoreComments;
    protected SQZHeader header;
    protected DataInput in;
    protected boolean closed = false;

    public static SQZReader open(InputStream is, boolean ignoreComments, String password) throws IOException {
        InputStream wrapped = new SHA1InputStream(is);
        SQZHeader header = SQZHeader.readHeader(wrapped);
        if (header.major == 1 && header.minor == 1) {
            return new SQZReader_1_1(wrapped, header, ignoreComments, password);
        }
        throw new IOException("Invalid major/minor SQZ version! (got: "+header.major+","+header.minor+")");
    }
    public static SQZReader open(String filename, boolean ignoreComments, String password) throws FileNotFoundException, IOException {
        return open(new FileInputStream(filename), ignoreComments, password);
    }
    public static SQZReader open(String filename, boolean ignoreComments) throws FileNotFoundException, IOException {
        return open(filename, ignoreComments, null);
    }
    public static SQZReader open(InputStream is, boolean ignoreComments) throws IOException {
        return open(is, ignoreComments, null);
    }
    
    public SQZReader(InputStream is, SQZHeader header, boolean ignoreComments, String password) throws IOException {
        this.header = header;
        this.ignoreComments = ignoreComments;

        if (header.deflate) {
            this.in = new DataInput(new InflaterInputStream(is));
        } else {
            this.in = new DataInput(new BufferedInputStream(is));
        }
    }

    public void close() throws IOException {
        closed = true;
        this.in.close();
    }

    public Iterator<FastqRead> iterator() {
        return new Iterator<FastqRead>() {
            private FastqRead one = null;
            private FastqRead two = null;
            private boolean first = true;
            
            private void loadData() {
                try {
                    if (header.paired) {
                        FastqRead[] pair = nextPair();
                        if (pair == null) {
                            one = null;
                            two = null;
                        } else {
                            one = pair[0];
                            two = pair[1];
                        }
                    } else {
                        one = nextRead();
                    }
                } catch (IOException e) {
                    one = null;
                    two = null;
                    e.printStackTrace();
                }
            }
            
            @Override
            public boolean hasNext() {
                if (first) {
                    loadData();
                    first = false;
                }
                return (one != null || two != null);
            }

            @Override
            public FastqRead next() {
                FastqRead out = null;
                if (one != null) {
                    out = one;
                    one = null;
                } else if (two != null) {
                    out = two;
                    two = null;
                }
                
                if (one == null && two == null) {
                    loadData();
                }
                
                return out;
            }

            @Override
            public void remove() {
            }};
    }
    public SQZHeader getHeader() {
        return header;
    }
}
