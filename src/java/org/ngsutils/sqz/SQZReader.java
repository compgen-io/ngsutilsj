package org.ngsutils.sqz;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.InflaterInputStream;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public abstract class SQZReader implements Iterable<FastqRead>{
    public abstract FastqRead[] nextRead() throws IOException;

    protected boolean ignoreComments;
    protected SQZHeader header;
    protected DataIO in;
    protected boolean closed = false;
    protected SQZInputStream inputStream;
    protected InputStream dataInputStream;

    public static SQZReader open(InputStream is, boolean ignoreComments, String password) throws IOException {
        SQZInputStream inputStream = new SQZInputStream(is);
        SQZHeader header = SQZHeader.readHeader(inputStream);
        if (header.major == 1 && header.minor == 1) {
            return new SQZReader_1_1(inputStream, header, ignoreComments, password);
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
    
    protected SQZReader(SQZInputStream inputStream, SQZHeader header, boolean ignoreComments, String password) throws IOException {
        this.inputStream = inputStream;
        this.header = header;
        this.ignoreComments = ignoreComments;

        if (header.deflate) {
            dataInputStream = new InflaterInputStream(inputStream);
        } else {
            dataInputStream = new BufferedInputStream(inputStream);
        }
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            dataInputStream.close();
        }
    }

    public Iterator<FastqRead> iterator() {
        return new Iterator<FastqRead>() {
            private FastqRead[] buf = null;
            private int pos = 0;
            private boolean first = true;
            
            private void loadData() {
                try {
                    buf = nextRead();
                    pos = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                    buf = null;
                }
            }
            
            @Override
            public boolean hasNext() {
                if (first) {
                    loadData();
                    first = false;
                }
                return (buf != null);
            }

            @Override
            public FastqRead next() {
                FastqRead out = buf[pos++];
                if (pos >= buf.length) {
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
    
    public byte[] getCalcDigest() throws IOException {
        return inputStream.getCalcDigest();
    }
    
    public byte[] getExpectedDigest() throws IOException {
        return inputStream.getExpectedDigest();
    }
    
}
