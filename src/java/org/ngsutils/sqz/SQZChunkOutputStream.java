package org.ngsutils.sqz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.io.DataIO;

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
    private boolean flushed = true;
    private int chunkCount = 0;
    
    private Set<String> textNames = new HashSet<String>();
    
    private boolean verbose = false;
    
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
        flushed = false;
        out.write(b);
    }
    
    public void writeTextBlock(String name, String str) throws IOException {
        if (textNames.contains(name)) {
            throw new IOException("A text block named: "+name+" has already been added!");
        }
        flush();
        reset();
        DataIO.writeString(out, name);
        DataIO.writeString(out, str);
        flushed = false;
        flush(SQZ.MAGIC_TEXT_CHUNK);
    }
    
    
    public void writeTextBlock(String name, InputStream is) throws IOException {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int count;
        while ((count = is.read(buf))> -1 ) {
            tmp.write(buf, 0, count);
        }
        is.close();
        tmp.close();
        String str = tmp.toString(DataIO.DEFAULT_ENCODING);
        writeTextBlock(name, str);
    }
    
    
    /**
     * +-------+----------+----------+-------+=================+
     * | magic | raw-sha1 | comp_len | magic | compressed data |
     * +-------+----------+----------+-------+=================+
     * 
     * Encrypted version
     *                                    |------- encrypted -------|       
     * +-------+----------+----+----------+-------+=================+
     * | magic | raw-sha1 | IV | comp_len | magic | compressed data |
     * +-------+----------+----+----------+-------+=================+
     *
     * Pulls compressed/encrypted data from backing bytearrayoutputstream and writes it
     * to the parent output stream
     * 
     * @param verbose
     * @throws IOException
     */
    public void flush() throws IOException {
        flush(SQZ.MAGIC_CHUNK);
    }
    public void flush(byte[] magic) throws IOException {
        if (flushed || baos == null) {
            return;
        }
        chunkCount ++;
        out.flush();
        out.close();
        DataIO.writeRawBytes(parent, magic);
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
        flushed = true;
    }
    
    /**
     *  Setup the output stream to compress and encrypt as needed.
     * @throws IOException
     */
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
        out = new DigestOutputStream(os, md);
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
