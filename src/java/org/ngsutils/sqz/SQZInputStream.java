package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.ngsutils.support.StringUtils;
import org.ngsutils.support.io.SuffixInputStream;

public class SQZInputStream extends InputStream{
    private final MessageDigest md;
    private final DigestInputStream dis;
    private final SuffixInputStream sis;
    private boolean closed = false;
    
    private byte[] digest = null;
    
    public SQZInputStream(InputStream parent) throws IOException {
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        sis = new SuffixInputStream(parent, md.getDigestLength());
        dis = new DigestInputStream(sis, md);
    }
    @Override
    public int read() throws IOException {
        return dis.read();
    }
    
    public void close() throws IOException {
        if (closed) {
            return;
        }
        dis.close();
        sis.close();
        digest = md.digest();
        byte[] known = sis.getSuffix();
        if (!Arrays.equals(known, digest)) {
            throw new IOException("Invalid SHA-1 signature for file! Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(known));
        }
        closed = true;
    }
    
    public byte[] getDigest() throws IOException {
        if (!closed) {
            throw new IOException("Stream not closed yet!");
        }
        return digest;
    }
}
