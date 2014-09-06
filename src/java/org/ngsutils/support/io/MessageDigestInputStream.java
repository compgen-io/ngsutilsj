package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.ngsutils.support.StringUtils;

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
public class MessageDigestInputStream extends InputStream {
    public static final int DEFAULT_BUFFERSIZE = 8192;
    final private InputStream parent;
    final private MessageDigest md;
    private boolean closed = false;
    private boolean finished = false;
    private byte[] buffer = null;
    
    private int pos=0;
    private int buflen=0;
    
    public MessageDigestInputStream(InputStream parent, MessageDigest md, int bufferSize) throws IOException {
        this.md = md;
        this.parent = parent;
        this.buffer = new byte[bufferSize];
    }

    public MessageDigestInputStream(InputStream parent, MessageDigest md) throws IOException {
        this(parent, md, DEFAULT_BUFFERSIZE);
    }

    @Override
    public int read() throws IOException {
        if (finished) {
            throw new IOException("Attempted to read from closed stream!");
        }
        if (pos >= buflen) {
            buflen = parent.read(buffer, 0, buffer.length);

            if (buflen == -1) {
                return -1;
            }
            md.update(buffer,0,buflen);
            pos = 0;
        }
        return buffer[pos++] & 0xff;
    }
    
    public void close() throws IOException {
        if (closed) {
            return;
        }
        if (pos > 0) {
            md.update(buffer, 0, pos);
        }
        parent.close();
        closed = true;
    }

    public static void main(String[] argv) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        MessageDigestInputStream mdis = new MessageDigestInputStream(System.in, md);
        int count = 0;
        while (mdis.read() != -1) {
            count++;
        }
        mdis.close();
        System.err.println("Read "+ count+" bytes");
        System.err.println(StringUtils.byteArrayToString(md.digest()));
    }
}
