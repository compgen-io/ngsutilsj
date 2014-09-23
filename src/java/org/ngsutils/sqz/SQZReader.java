package org.ngsutils.sqz;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Iterator;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;

public abstract class SQZReader implements FastqReader {
    public abstract FastqRead[] nextRead() throws IOException;
    
    protected SQZInputStream sis;
    protected SQZHeader header;
    protected SQZChunkInputStream dcis;
    protected Exception exception = null;
    protected MessageDigest md;
    
    protected boolean ignoreComments;
    protected boolean closed = false;
    
    protected boolean verbose = false;

    public static SQZReader open(InputStream parent, boolean ignoreComments, String password, boolean verbose) throws IOException, GeneralSecurityException {

        SQZInputStream sis = new SQZInputStream(parent);
        
        SQZHeader header = SQZHeader.readHeader(sis);
        if (header.major == 1 && header.minor == 1) {
            return new SQZReader_1_1(sis, header, ignoreComments, password, verbose);
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
    public static SQZReader open(InputStream is, boolean ignoreComments, String password) throws IOException, GeneralSecurityException {
        return open(is, ignoreComments, password, false);
    }
    
    protected SQZReader(SQZInputStream sis, SQZHeader header, boolean ignoreComments, String password, boolean verbose) throws IOException, GeneralSecurityException {
        this.sis = sis;
        this.header = header;
        this.ignoreComments = ignoreComments;

        Cipher cipher = null;
        SecretKeySpec secret = null;
        int ivLen = 0;
                
        
        if (header.encryption != null && password == null) {
            throw new IOException("Missing password for encrypted file!");
        } else if (header.encryption == null && password != null) {
            throw new IOException("Given a password for an unencrypted file!");
        } else if (header.encryption != null && (header.encryption.equals("AES-128") || header.encryption.equals("AES-256"))) { 
            byte[] salt = new byte[32];
            sis.read(salt);
            ivLen = 16;
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            int keysize;
            if (header.encryption.equals("AES-128")) {
                keysize = 128;
            } else if (header.encryption.equals("AES-256")) {
                keysize = 256;
            } else {
                throw new IOException("Unknown encryption type: "+header.encryption);

            }
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, keysize);
            SecretKey tmp = factory.generateSecret(spec);
            secret = new SecretKeySpec(tmp.getEncoded(), "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } else if (header.encryption != null) {
            throw new IOException("Unknown encryption type: "+ header.encryption);
        }

        dcis = new SQZChunkInputStream(sis, header.compressionType, cipher, secret, ivLen, verbose);
    }

    public void close() throws IOException {
        if (!closed) {
            dcis.close();
            sis.close();
            closed = true;
        }
    }

    public byte[] getDigest() throws IOException {
        return sis.getDigest();
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
    
    public String getText(String name) {
        return dcis.getText(name);
    }
    
    public Set<String> getTextNames() {
        return dcis.getTextNames();
    }
    
    public Exception getException() {
        return exception;
    }
    
    public void fetchText() {
        try {
            dcis.readAllChunks();
        } catch (IOException e) {
            this.exception = e;
        }
    }
    
}
