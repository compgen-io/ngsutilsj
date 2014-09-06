package org.ngsutils.sqz;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Iterator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.ngsutils.fastq.FastqRead;

public abstract class SQZReader implements Iterable<FastqRead>{
    public abstract FastqRead[] nextRead() throws IOException;
    
    protected SQZHeader header;
    protected SQZChunkInputStream dcis;
    protected Exception exception = null;
    
    protected boolean ignoreComments;
    protected boolean closed = false;
    
    protected boolean verbose = false;

    public static SQZReader open(InputStream is, boolean ignoreComments, String password, boolean verbose) throws IOException, GeneralSecurityException {
        SQZHeader header = SQZHeader.readHeader(is);
        if (header.major == 1 && header.minor == 1) {
            return new SQZReader_1_1(is, header, ignoreComments, password, verbose);
        }
        throw new IOException("Invalid major/minor SQZ version! (got: "+header.major+","+header.minor+")");
    }
    public static SQZReader open(String filename, boolean ignoreComments, String password, boolean verbose) throws FileNotFoundException, IOException, GeneralSecurityException {
        return open(new FileInputStream(filename), ignoreComments, password, verbose);
    }
    public static SQZReader open(String filename, boolean ignoreComments) throws FileNotFoundException, IOException, GeneralSecurityException {
        return open(filename, ignoreComments, null, false);
    }
    public static SQZReader open(InputStream is, boolean ignoreComments) throws IOException, GeneralSecurityException {
        return open(is, ignoreComments, null, false);
    }
    
    protected SQZReader(InputStream is, SQZHeader header, boolean ignoreComments, String password, boolean verbose) throws IOException, GeneralSecurityException {
        this.header = header;
        this.ignoreComments = ignoreComments;

        Cipher cipher = null;
        SecretKeySpec secret = null;
        int ivLen = 0;
                
        
        if (header.encryption != null && password == null) {
            throw new IOException("Missing password for encrypted file!");
        } else if (header.encryption == null && password != null) {
            throw new IOException("Given a password for an unencrypted file!");
        } else if (header.encryption != null && header.encryption.equals("AES-128")) { 
            byte[] salt = new byte[32];
            is.read(salt);
            ivLen = 16;
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
            SecretKey tmp = factory.generateSecret(spec);
            secret = new SecretKeySpec(tmp.getEncoded(), "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } else if (header.encryption != null) {
            throw new IOException("Unknown encryption type!");
        }

        dcis = new SQZChunkInputStream(is, header.compressionType, cipher, secret, ivLen, verbose);
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            dcis.close();
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
                    exception = e;
                    
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
            public FastqRead next(){
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
    public int getChunkCount() {
        return dcis.getChunkCount();   
    }
    
    public Exception getException() {
        return exception;
    }
    
}
