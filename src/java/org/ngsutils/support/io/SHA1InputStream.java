package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Writes all input bytes to the parent output stream. When closed, this will write a sha1 hash
 * to the end of the parent stream.
 * 
 * To do this, we'll keep two buffers... one of BUFFER_SIZE, the other of DIGESTLENGTH.
 * 
 * Each time we read from the parent inputstream, we'll copy over the last DIGESTLENGTH bytes to the hashBuffer.
 * 
 * When the parent is exhausted, we can verify that the SHA1 digest matches what's in the hashBuffer.
 * 
 * @author mbreese
 *
 */
public class SHA1InputStream extends InputStream {
    public static final int DEFAULT_BUFFERSIZE = 32;
    private InputStream parent;
    private MessageDigest m;
    private int mlen;
    private boolean closed = false;
    private byte[] buffer = null;
    private byte[] bufferHash = null;
    
    private int pos=0;
    private int buflen=0;
    
    public SHA1InputStream(InputStream parent, int bufferSize) throws IOException {
        try {
            this.m = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        
        this.parent = parent;
        this.mlen = m.getDigestLength();
        this.buffer = new byte[bufferSize];
        this.bufferHash = new byte[mlen];
        
        int read = parent.read(bufferHash);
        if (read == -1) {
            throw new IOException("Stream is exhausted before SHA1!");
        } 
//        System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));
    }
    public SHA1InputStream(InputStream parent) throws IOException {
        this(parent, DEFAULT_BUFFERSIZE);
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Attempted to read from closed stream!");
        }
        if (pos >= buflen) {
            // fill the end of the buffer
//            System.err.println("buffer0   : "+ StringUtils.join(" ", buffer));
            buflen = parent.read(buffer, mlen, buffer.length - mlen);

//            System.err.println("buffer1   : "+ StringUtils.join(" ", buffer));

            if (buflen == -1) {
                return -1;
            }
            
//            System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));

            // copy the hash buffer to the main buffer (prefix)
            for (int i=0; i<bufferHash.length; i++) {
                buffer[i] = bufferHash[i];
            }

//            System.err.println("buffer2   : "+ StringUtils.join(" ", buffer));

            // copy the end of the buffer to the hash buffer
            for (int i=0; i<mlen; i++) {
                bufferHash[i] = buffer[buflen + i];
                buffer[buflen+i] = -1;
            }

//            System.err.println("buffer3   : "+ StringUtils.join(" ", buffer));
//            System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));

            pos = 0;
            m.update(buffer,0,buflen);
        }
        
//        System.err.println("["+pos+"] "+String.format("%02X",  buffer[pos]));
        
        return buffer[pos++] & 0xff;
    }

    @Override
    public void close() throws IOException {
//        System.err.println("Closing...");
//        System.err.println("["+pos+"]   "+ StringUtils.join(" ", buffer));
//        System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));
        byte[] calcHash = m.digest();
        parent.close();
        
        if (!Arrays.equals(calcHash, bufferHash)) {
            String given = new BigInteger(1, bufferHash).toString(16);
            String calc = new BigInteger(1, calcHash).toString(16);
            while (given.length() < (mlen*2)) {
                given = "0"+given;
            }
            while (calc.length() < (mlen*2)) {
                calc = "0"+calc;
            }
            throw new IOException("Warning!!! SHA1 hashes differ!\nGiven: "+given+"\nCalc : "+calc+"\n");
        }
        closed = true;
    }
}
