package org.ngsutils.sqz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.io.DataIO;
import org.ngsutils.support.io.MessageDigestOutputStream;

public class SQZChunkOutputStream extends OutputStream {

    public static final int DEFAULT_BUFFERSIZE = 8*1024; // this will grow as needed, so a small buffer is fine...
    private final OutputStream parent;
    private final Cipher cipher;
    private final SecretKeySpec secret;
    private final MessageDigest md;
    private final int compressionType;

    private OutputStream out = null;
    private ByteArrayOutputStream baos = null;
    private boolean closed = false;
    private int chunkCount = 0;
    
    public SQZChunkOutputStream(OutputStream parent, int compressionType, Cipher cipher, SecretKeySpec secret, int bufferSize) throws NoSuchAlgorithmException, IOException {
        this.parent = parent;
        this.cipher = cipher;
        this.secret = secret;
        this.compressionType = compressionType;
        this.md = MessageDigest.getInstance("SHA-1");
    }

    public SQZChunkOutputStream(OutputStream parent, int compressionType, Cipher cipher, SecretKeySpec secret) throws NoSuchAlgorithmException, IOException {
        this(parent, compressionType, cipher, secret, DEFAULT_BUFFERSIZE);
    }

    @Override
    public void write(int b) throws IOException {
        if (baos == null) {
            reset();
        }
        out.write(b);
    }
    
    /**
     * Flush the current buffer. Compresses the data using deflate/bzip2, and if available, uses the cipher
     * to encrypt the data. The stored SHA-1 is the SHA-1 for the uncompressed/unencrypted data
     * 
     *                               |--compressed--|
     * +-------+----------+----------+=======+======+
     * | magic | raw-sha1 | comp_len | magic | data |
     * +-------+----------+----------+=======+======+
     * 
     * Encrypted version
     *                                    |--encrypted---|       
     *                                    |--compressed--|       
     * +-------+----------+----+----------+=======+======+
     * | magic | raw-sha1 | IV | comp_len | magic | data |
     * +-------+----------+----+----------+=======+======+
     */
    
    public void flush() throws IOException {
        flush(false);
    }

    public void flush(boolean verbose) throws IOException {
        chunkCount ++;
        out.flush();
        out.close();
        DataIO.writeRawBytes(parent, SQZ.MAGIC_CHUNK);
        byte[] digest = md.digest();
        DataIO.writeRawBytes(parent, digest);

        if (cipher != null && secret != null) {
            DataIO.writeRawBytes(parent, cipher.getIV());
        }

        if (verbose) {
            System.err.println("Writing chunk #"+chunkCount+" ("+baos.size()+" bytes) SHA1:" + StringUtils.byteArrayToString(digest));
        }

        byte[] outbuf = baos.toByteArray();
        DataIO.writeByteArray(parent,  outbuf);

        // reset the output buffer
        baos = null;
        out = null;
    }
    
    private void reset() throws IOException {
        this.baos = new ByteArrayOutputStream();
        OutputStream os = baos;
        
        if (cipher != null && secret != null) {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secret);
            } catch (InvalidKeyException e) {
                throw new IOException(e);
            }
            os = new CipherOutputStream(os, cipher);
        }

        os.write(SQZ.MAGIC_CHUNK_DATA);

        if (compressionType == SQZ.COMPRESS_DEFLATE) {
            os = new DeflaterOutputStream(os);
        } else if (compressionType == SQZ.COMPRESS_BZIP2) {
            os = new BZip2CompressorOutputStream(os);
        }
        out = new MessageDigestOutputStream(os, md);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        if (baos != null) {
            flush();
        }
        parent.close();
        closed = true;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
