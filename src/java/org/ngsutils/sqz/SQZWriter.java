package org.ngsutils.sqz;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public class SQZWriter {
    public static final int MAJOR = 1;
    public static final int MINOR = 1;

    protected SQZOutputStream sqzos;
    protected OutputStream dataOutputStream;
    protected boolean closed = false;
    
    public final int flags;
    public final SQZHeader header;
    
    public SQZWriter(OutputStream os, int flags, int seqCount, String encryption, String password) throws IOException, GeneralSecurityException {
        sqzos = new SQZOutputStream(os);
        dataOutputStream = sqzos;
        
        this.flags = flags;
        header = new SQZHeader(MAJOR, MINOR, flags, seqCount, encryption);
        header.writeHeader(dataOutputStream);

        if (encryption != null && encryption.equals("AES-128")) {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

            SecureRandom random = new SecureRandom();
            byte[] salt = random.generateSeed(32);
            
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);

            dataOutputStream.write(salt);
            dataOutputStream.write(cipher.getIV());
            
            dataOutputStream = new CipherOutputStream(dataOutputStream, cipher);

        } else if (encryption != null) {
            throw new IOException("Unknown encryption type!");
        }
        
        dataOutputStream.write(SQZ.DATA_MAGIC);

        if (header.deflate) {
            dataOutputStream = new DeflaterOutputStream(dataOutputStream);
        }

    }

    public SQZWriter(OutputStream out, int flags, int seqCount) throws IOException, GeneralSecurityException {
        this(out, flags, seqCount, null, null);
    }

    public SQZWriter(String filename, int flags, int seqCount, String encryptionAlgorithm, String password) throws IOException, GeneralSecurityException {
        this(new FileOutputStream(filename), flags, seqCount, encryptionAlgorithm, password);
    }

    public SQZWriter(String filename, int flags, int seqCount) throws IOException, GeneralSecurityException {
        this(new FileOutputStream(filename), flags, seqCount);
    }
    
    public void close() throws IOException {
        if (!closed) {
            dataOutputStream.close();
            closed = true;
        }
    }
    
    public void writeReads(List<FastqRead> reads) throws IOException {
        if (closed) {
            throw new IOException("Tried to write to closed file!");
        }
        
        if (reads.size() != header.seqCount) {
            throw new IOException("Each record must have " + header.seqCount + " reads!");            
        }
        
        for (int i=1; i<reads.size(); i++) {
            if (!reads.get(i).getName().equals(reads.get(0).getName())) {
                throw new IOException("Reads must have the same name!");
            }
        }

        DataIO.writeString(dataOutputStream, reads.get(0).getName());

        if (header.hasComments) {
            for (FastqRead read: reads) {
                if (read.getComment() == null) {
                    DataIO.writeString(dataOutputStream, "");
                } else {
                    DataIO.writeString(dataOutputStream, read.getComment());
                }
            }
        }

        for (FastqRead read: reads) {
            DataIO.writeByteArray(dataOutputStream, SQZ.combineSeqQual(read.getSeq(), read.getQual()));
        }
    }
    
    public byte[] getDigest() throws IOException {
        return sqzos.getDigest();
    }
}
