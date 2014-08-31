package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.ngsutils.support.io.MessageDigestInputStream;
import org.ngsutils.support.io.SuffixInputStream;

/**
 * The SQZ input stream is tracked by a SHA1 hash. A stored digest is located in the last 20 bytes of the parent
 * InputStream. Because of this, we will wrap the parent stream in a SuffixInputStream to keep the last 20 bytes
 * out of the MessageDigestInputStream(). 
 * @author mbreese
 *
 */
public class SQZInputStream extends InputStream {
    final protected MessageDigestInputStream sha1;
    final protected SuffixInputStream suffix;
    
    public SQZInputStream(InputStream parent) throws IOException {
        MessageDigest m;
        try {
            m = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        suffix = new SuffixInputStream(parent, m.getDigestLength());
        sha1 = new MessageDigestInputStream(suffix, m);
    }

    public void close() throws IOException {
        sha1.close();
    }
    
    @Override
    public int read() throws IOException {
        return sha1.read();
    }
    
    public byte[] getCalcDigest() throws IOException {
        return sha1.getDigest();
    }
    public byte[] getExpectedDigest() throws IOException {
        return suffix.getSuffix();
    }
}
