package org.ngsutils.sqz;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public abstract class SQZReader implements Iterable<FastqRead>{
    public abstract FastqRead[] nextRead() throws IOException;

    protected boolean ignoreComments;
    protected SQZHeader header;
    protected DataIO in;
    protected boolean closed = false;
    protected SQZInputStream sqzis;
    protected InputStream dataInputStream;

    public static SQZReader open(InputStream is, boolean ignoreComments, String password) throws IOException, GeneralSecurityException {
        SQZInputStream sqzis = new SQZInputStream(is);
        
        SQZHeader header = SQZHeader.readHeader(sqzis);
        if (header.major == 1 && header.minor == 1) {
            return new SQZReader_1_1(sqzis, header, ignoreComments, password);
        }
        throw new IOException("Invalid major/minor SQZ version! (got: "+header.major+","+header.minor+")");
    }
    public static SQZReader open(String filename, boolean ignoreComments, String password) throws FileNotFoundException, IOException, GeneralSecurityException {
        return open(new FileInputStream(filename), ignoreComments, password);
    }
    public static SQZReader open(String filename, boolean ignoreComments) throws FileNotFoundException, IOException, GeneralSecurityException {
        return open(filename, ignoreComments, null);
    }
    public static SQZReader open(InputStream is, boolean ignoreComments) throws IOException, GeneralSecurityException {
        return open(is, ignoreComments, null);
    }
    
    protected SQZReader(SQZInputStream sqzis, SQZHeader header, boolean ignoreComments, String password) throws IOException, GeneralSecurityException {
        this.sqzis = sqzis;
        dataInputStream = sqzis;
        this.header = header;
        this.ignoreComments = ignoreComments;

        if (header.encryption != null && password == null) {
            throw new IOException("Missing password for encrypted file!");
        } else if (header.encryption == null && password != null) {
            throw new IOException("Given a password for an unencrypted file!");
        } else if (header.encryption != null && header.encryption.equals("AES128")) { 
            byte[] salt = new byte[32];
            byte[] iv = new byte[16];
            
            dataInputStream.read(salt);
            dataInputStream.read(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));

            dataInputStream = new CipherInputStream(dataInputStream, cipher);
        } else if (header.encryption != null) {
            throw new IOException("Unknown encryption type!");
        }

        byte[] magic = DataIO.readRawBytes(dataInputStream, 4);
        if (!Arrays.equals(magic, SQZ.DATA_MAGIC)) {
            try {
                close();
            } catch (IOException e) {
            }
            throw new IOException("Invalid encryption password!");
        }

        if (header.deflate) {
            dataInputStream = new InflaterInputStream(dataInputStream);
        }
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            dataInputStream.close();
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
                    e.printStackTrace();
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
            public FastqRead next() {
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
    
    public byte[] getCalcDigest() throws IOException {
        return sqzis.getCalcDigest();
    }
    
    public byte[] getExpectedDigest() throws IOException {
        return sqzis.getExpectedDigest();
    }
    
}
