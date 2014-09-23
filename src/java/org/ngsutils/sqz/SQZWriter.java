package org.ngsutils.sqz;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public class SQZWriter {
    public static final int MAJOR = 1;
    public static final int MINOR = 1;

    protected MessageDigest md;
    protected OutputStream parent;
    protected SQZChunkOutputStream dcos=null;
    protected boolean closed = false;
    
    protected int readCount = 0;
    protected int chunkSize = 10000;
    
    public final int flags;
    public final SQZHeader header;
    
    public SQZWriter(OutputStream parent, int flags, int seqCount, int compressionType, String encryption, String password) throws IOException, GeneralSecurityException {
        this.parent = parent;
        this.flags = flags;
        
        md = MessageDigest.getInstance("SHA-1");
        OutputStream os = new DigestOutputStream(parent, md);
        
        header = new SQZHeader(MAJOR, MINOR, flags, seqCount, compressionType, encryption);
        header.writeHeader(os);

        Cipher cipher = null;
        SecretKeySpec secret = null;
      
        if (encryption != null && (encryption.equals("AES-128") || encryption.equals("AES-256"))) {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            SecureRandom random = new SecureRandom();
            byte[] salt = random.generateSeed(32);
            int keysize;
            if (encryption.equals("AES-128")) {
                keysize = 128;
            } else if (encryption.equals("AES-256")) {
                keysize = 256;
            } else {
                throw new IOException("Unknown encryption type: "+encryption);

            }
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, keysize);
            SecretKey tmp = factory.generateSecret(spec);
            secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            try {
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            } catch (InvalidKeyException e) {
                throw new IOException("Invalid key length! Did you try to use 256 bit encryption without installing the Unlimited JCE policy? ");
                
            }

            os.write(salt);

        } else if (encryption != null) {
            throw new IOException("Unknown encryption type: "+encryption);
        }
        
        dcos = new SQZChunkOutputStream(os, compressionType, cipher, secret);
    }

    public SQZWriter(OutputStream out, int flags, int seqCount) throws IOException, GeneralSecurityException {
        this(out, flags, seqCount, SQZ.COMPRESS_DEFLATE, null, null);
    }

    public SQZWriter(String filename, int flags, int seqCount, int compressionType, String encryptionAlgorithm, String password) throws IOException, GeneralSecurityException {
        this(new FileOutputStream(filename), flags, seqCount, compressionType, encryptionAlgorithm, password);
    }

    public SQZWriter(String filename, int flags, int seqCount) throws IOException, GeneralSecurityException {
        this(new FileOutputStream(filename), flags, seqCount);
    }
    
    public void close() throws IOException {
        if (!closed) {
            dcos.flush();
            
            byte[] digest = md.digest();
            DataIO.writeRawBytes(parent, digest);
            parent.close();
            
            closed = true;
        }
    }
    
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }
    
    public void writeReads(List<FastqRead> reads) throws IOException {
        writeReads(reads, false);
    }
    public void writeReads(List<FastqRead> reads, boolean verbose) throws IOException {
        if (closed) {
            throw new IOException("Tried to write to closed file!");
        }
        
        if (reads.size() != header.seqCount) {
            throw new IOException("Each record must have " + header.seqCount + " reads!");            
        }
        
        if (dcos != null) {
            readCount ++;        
            if (readCount > chunkSize ) {
                dcos.flush();
                readCount = 0;
            }
        }
        
        for (int i=1; i<reads.size(); i++) {
            if (!reads.get(i).getName().equals(reads.get(0).getName())) {
                throw new IOException("Reads must have the same name!");
            }
        }

        DataIO.writeString(dcos, reads.get(0).getName());

        if (header.hasComments) {
            for (FastqRead read: reads) {
                if (read.getComment() == null) {
                    DataIO.writeString(dcos, "");
                } else {
                    DataIO.writeString(dcos, read.getComment());
                }
            }
        }

        try {
            for (FastqRead read: reads) {
                if (header.colorspace) {
                    DataIO.writeByteArray(dcos, SQZ.combineSeqQualColorspace(read.getSeq(), read.getQual()));
                } else {
                    DataIO.writeByteArray(dcos, SQZ.combineSeqQual(read.getSeq(), read.getQual()));
                }
            }
        } catch (SQZException e) {
            throw new IOException(e);
        }
    }
    
    public void writeText(String name, String s) throws IOException {
        dcos.writeTextBlock(name, s);
    }
    
    public int getChunkCount() {
        return dcos.getChunkCount();
    }

    public void writeText(String name, FileInputStream fis) throws IOException {
        dcos.writeTextBlock(name, fis);
    }
}