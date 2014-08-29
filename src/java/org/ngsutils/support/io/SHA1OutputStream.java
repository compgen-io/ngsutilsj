package org.ngsutils.support.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Writes all input bytes to the parent output stream. When closed, this will write a sha1 hash
 * to the end of the parent stream.
 * @author mbreese
 *
 */
public class SHA1OutputStream extends OutputStream {
    private OutputStream parent;
    private MessageDigest m;
    private boolean closed = false;
    private byte[] hash = null;
    
    private byte[] buffer;
    private int pos = 0;
    
    public SHA1OutputStream(OutputStream parent, int bufferSize) throws IOException {
        try {
            this.m = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        this.parent = parent;
        this.buffer = new byte[bufferSize];
    }
    public SHA1OutputStream(OutputStream parent) throws IOException {
        this(parent, SHA1InputStream.DEFAULT_BUFFERSIZE);
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed!");
        }
        if (pos >= buffer.length) {
            m.update(buffer);
            parent.write(buffer);
            pos = 0;
        }
        buffer[pos++] = (byte) (b & 0xFF);
    }

    @Override
    public void close() throws IOException {
        if (pos > 0) {
            m.update(buffer, 0, pos);
            parent.write(buffer,0,pos);
        }
        hash = m.digest();
        parent.write(hash);
        
        parent.close();
        closed = true;
    }
}
