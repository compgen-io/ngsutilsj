package org.ngsutils.sqz;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.ngsutils.support.io.MessageDigestOutputStream;

/**
 * The SQZOutputStream is wrapped in an MessageDigestOutputStream. When SQZOutputStream is closed, it closes the
 * MessageDigestOutputStream and writes the SHA1 digest to the parent OutputStream as a suffix.
 *
 * @author mbreese
 *
 */
public class SQZOutputStream extends OutputStream {
    final protected OutputStream parent;
    final protected MessageDigestOutputStream mdos;
    private boolean closed = false;
    
    public SQZOutputStream(OutputStream parent) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        this.parent = parent;
        mdos = new MessageDigestOutputStream(parent, md);
    }
    
    public void close() throws IOException {
        if (closed) {
            return;
        }
        
        mdos.finish();
        byte[] digest = mdos.getDigest();
        parent.write(digest);
        parent.close();
        closed = true;
    }
    
    public byte[] getDigest() throws IOException {
        return mdos.getDigest();
    }

    @Override
    public void write(int b) throws IOException {
        mdos.write(b);
    }
}
