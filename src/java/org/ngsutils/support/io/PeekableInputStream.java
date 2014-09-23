package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;

import org.ngsutils.support.StringUtils;

/**
 * Will assume that there are suffixLen bytes of suffix at the end of the proper inputstream.
 * This class will pass everything read everything from the parent inputstream *except* 
 * the last suffixLen bytes of suffix.
 * 
 * This is useful for reading a variable length stream with a fixed length suffix block, such
 * as a stream with an MD5/SHA1 hash digest of a stream appended to the stream itself.
 * 
 * @author mbreese
 */
public class PeekableInputStream extends InputStream {
    public static final int DEFAULT_BUFFERSIZE = 32;
    protected final int bufferSize;
    protected final InputStream parent;

    private boolean closed = false;
    private byte[] buffer = null;

    private int pos=0;
    private int buflen=0;
    
    public PeekableInputStream(InputStream parent, int bufferSize) throws IOException {
        this.parent = parent;
        this.bufferSize = bufferSize;
        this.buffer = new byte[bufferSize];
    }
    public PeekableInputStream(InputStream parent) throws IOException {
        this(parent, DEFAULT_BUFFERSIZE);
    }

    private void fillbuffer() throws IOException {
        if (pos >= buflen) { 
            buflen = parent.read(buffer, 0, buffer.length);
            pos = 0;
        } else if (pos > 0) {
            byte[] tmp = new byte[buffer.length];
            for (int i=0; i<buflen - pos; i++) {
                tmp[i] = buffer[pos + i];
            }
            
            buffer = tmp;
            
            int read = parent.read(buffer, pos, buffer.length);

            if (read > 0) {
                buflen = read + pos;
            } else {
                buflen = pos;
            }
            pos = 0;
        }
        
//        System.err.println("Current buffer: "+ StringUtils.byteArrayToString(buffer));
        
    }
    
    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Attempted to read from closed stream!");
        }
        if (pos >= buflen) { 
            fillbuffer();
        }
        
        if (buflen == -1) {
            return -1;
        }
        
        return buffer[pos++] & 0xff;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        parent.close();
        closed = true;
    }
    
    /**
     * Preview a few bytes from the stream before actually "reading" them.
     * 
     * This will let you read a few bytes from a stream (which might be a file, 
     * network, or whatever) without removing the bytes from the stream. This way
     * you can pre-identify a file with magic bytes without removing the magic bytes.
     * 
     * @param bytes
     * @return
     * @throws IOException
     */
    public byte[] peak(int bytes) throws IOException {
        if (closed) {
            throw new IOException("Attempted to read from closed stream!");
        }
        if (pos > 0 || pos >= buflen) {
            fillbuffer();
        }
        
        byte[] out = new byte[bytes];
        
        for (int i=0; i<bytes; i++) {
            out[i] = buffer[i+pos];
        }
        
        return out;
    }
}
