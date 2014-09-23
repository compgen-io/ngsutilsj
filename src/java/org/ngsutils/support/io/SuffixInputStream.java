package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;

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
public class SuffixInputStream extends InputStream {
    public static final int DEFAULT_BUFFERSIZE = 8192;
    private InputStream parent;

    private int suffixLen;
    private boolean closed = false;
    private byte[] buffer = null;
    private byte[] bufferSuffix = null;
    
    private int pos=0;
    private int buflen=0;
    
    public SuffixInputStream(InputStream parent, int suffixLen, int bufferSize) throws IOException {
        if (suffixLen >= bufferSize) {
            throw new IOException("The buffer size must be bigger than the suffix length!");
        }

        this.parent = parent;
        this.suffixLen = suffixLen;
        this.buffer = new byte[bufferSize+suffixLen];
        this.bufferSuffix = new byte[suffixLen];
        
        int total = 0;
        int count = 0;

        // read a full buffer length first - we want to read only in bufferSize blocks
        while ((count = parent.read(buffer, total, bufferSize-total)) != -1) {
            total += count;
        }

        if (total == -1 || total < suffixLen) {
            throw new IOException("Stream is exhausted before suffix!");
        }
        
        // copy the suffixLen bytes from the end of the buffer.
        for (int i=0; i<suffixLen; i++) {
            bufferSuffix[i] = buffer[total - suffixLen + i];
        }
        
        buflen = total - suffixLen;
        pos = 0;
        
//        System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));
    }
    public SuffixInputStream(InputStream parent, int suffixLen) throws IOException {
        this(parent, suffixLen, DEFAULT_BUFFERSIZE);
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Attempted to read from closed stream!");
        }
        if (pos >= buflen) {
            // fill the end of the buffer
//            System.err.println("buffer0   : "+ StringUtils.join(" ", buffer));
            buflen = parent.read(buffer, suffixLen, buffer.length - suffixLen);

//            System.err.println("buffer1   : "+ StringUtils.join(" ", buffer));

            if (buflen == -1) {
                return -1;
            }
            
//            System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));

            // copy the hash buffer to the main buffer (prefix)
            for (int i=0; i<bufferSuffix.length; i++) {
                buffer[i] = bufferSuffix[i];
            }

//            System.err.println("buffer2   : "+ StringUtils.join(" ", buffer));

            // copy the end of the buffer to the hash buffer
            for (int i=0; i<suffixLen; i++) {
                bufferSuffix[i] = buffer[buflen + i];
                buffer[buflen+i] = -1;
            }

//            System.err.println("buffer3   : "+ StringUtils.join(" ", buffer));
//            System.err.println("bufferHash: "+ StringUtils.join(" ", bufferHash));

            pos = 0;
        }
        
//        System.err.println("["+pos+"] "+String.format("%02X",  buffer[pos]));
        return buffer[pos++] & 0xff;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        parent.close();
        closed = true;
    }
    
    public byte[] getSuffix() throws IOException {
        if (closed) {
            return bufferSuffix;
        }
        throw new IOException("Stream hasn't been closed yet");
    }
}
