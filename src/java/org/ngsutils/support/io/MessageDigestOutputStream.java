package org.ngsutils.support.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.ngsutils.support.StringUtils;

/**
 * Writes all input bytes to the parent output stream. When closed, this will write a sha1 hash
 * to the end of the parent stream.
 * @author mbreese
 *
 */
public class MessageDigestOutputStream extends OutputStream {
    private OutputStream parent;
    private MessageDigest md;
    private boolean finished = false;
    private boolean closed = false;
    private byte[] hash = null;
    
    private byte[] buffer;
    private int pos = 0;
    
    public MessageDigestOutputStream(OutputStream parent, MessageDigest md, int bufferSize) throws IOException {
        this.parent = parent;
        this.md = md;
        this.buffer = new byte[bufferSize];
    }
    public MessageDigestOutputStream(OutputStream parent, MessageDigest md) throws IOException {
        this(parent, md, MessageDigestInputStream.DEFAULT_BUFFERSIZE);
    }

    @Override
    public void write(int b) throws IOException {
        if (finished) {
            throw new IOException("Stream is closed!");
        }
        if (pos >= buffer.length) {
            md.update(buffer);
            parent.write(buffer);
            pos = 0;
        }
        buffer[pos++] = (byte) (b & 0xFF);
    }

    public void finish() throws IOException {
        if (finished) {
            return;
        }
        if (pos > 0) {
            md.update(buffer, 0, pos);
            parent.write(buffer,0,pos);
        }

        hash = md.digest();
        finished = true;
    }
    
    public void close() throws IOException {
        if (closed) {
            return;
        }
        finish();
        parent.close();
        closed = true;
    }

    
    public byte[] getDigest() throws IOException {
        if (!finished) {
            throw new IOException("Stream not closed!");
        }
        return hash;
    }
    
    public static void main(String[] argv) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        MessageDigestOutputStream mdos = new MessageDigestOutputStream(System.out, md);
        int b;
        while ((b = System.in.read()) != -1) {
            byte by = (byte) (b & 0xFF); 
            mdos.write(by);
        }
        mdos.finish();
        System.err.println(StringUtils.digestToString(mdos.getDigest()));
        mdos.close();
    }
}
