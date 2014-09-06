package org.ngsutils.sqz;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.io.DataIO;
import org.ngsutils.support.io.MessageDigestInputStream;

public class SQZChunkInputStream extends InputStream {
    private final InputStream parent;
    private final Cipher cipher;
    private final SecretKeySpec secret;
    private final int compressionType;
    private final int ivLen;
    private final MessageDigest md;

    private InputStream wrapped = null;;

    private byte[] buffer = new byte[8192];
    private byte[] chunkDigest = null;
    private int pos = 0;
    private int buflen = 0;
    private boolean closed = false;
    private int chunkCount = 0;
    
    public SQZChunkInputStream(InputStream parent, int compressionType, Cipher cipher, SecretKeySpec secret, int ivLen) throws NoSuchAlgorithmException, IOException {
        this.parent = parent;
        this.cipher = cipher;
        this.secret = secret;
        this.ivLen = ivLen;
        this.compressionType = compressionType;
        this.md = MessageDigest.getInstance("SHA-1");
    }


    @Override
    public int read() throws IOException {
        if (pos >= buflen) {
            if (wrapped != null) {
                buflen = wrapped.read(buffer);
            }

            if (wrapped == null || buflen < 1) {
                if (chunkDigest != null) {
                    byte[] digest = md.digest();
                    System.err.println("SHA-1 Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(chunkDigest));
                    if (!Arrays.equals(chunkDigest, digest)) {
                        throw new IOException("Invalid SHA-1 signature for block "+chunkCount+" Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(chunkDigest));
                    }
                }
                
                if (!readChunk()) {
                    return -1;
                }
                
                buflen = wrapped.read(buffer);
                if (buflen < 1) {
                    return buflen;
                }
            }
            pos = 0;
        }

        return buffer[pos++] & 0xFF;
    }
    
    
    public void findNextChunk() throws IOException {
        byte one = (byte) (parent.read() & 0xFF);
        byte two = (byte) (parent.read() & 0xFF);
        byte three = (byte) (parent.read() & 0xFF);
        byte four = (byte) (parent.read() & 0xFF);
        
        while (one != SQZ.MAGIC_CHUNK[0] && two != SQZ.MAGIC_CHUNK[1] && three != SQZ.MAGIC_CHUNK[2] && four != SQZ.MAGIC_CHUNK[3]) {
            byte tmp = (byte) (parent.read() & 0xFF);
            one = two;
            two = three;
            three = four;
            four = tmp;
        }
        
        readChunk(true);
    }
    
    
    /**
     * Chunk format:                     
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
    
    protected boolean readChunk() throws IOException {
        return readChunk(false);
    }
    
    protected boolean readChunk(boolean nomagic) throws IOException {
        byte[] magic = DataIO.readRawBytes(parent, SQZ.MAGIC_CHUNK.length);
        if (magic == null) {
            return false;
        }
        if (!Arrays.equals(magic, SQZ.MAGIC_CHUNK)) {
            throw new IOException("Invalid chunk! " + chunkCount + " Magic: " + StringUtils.byteArrayToString(magic));
        }

        chunkDigest = DataIO.readRawBytes(parent, md.getDigestLength());

        if (cipher != null && secret != null) {
            byte[] iv = DataIO.readRawBytes(parent, ivLen);
            try {
                cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                throw new IOException(e);
            }
        }

        chunkCount ++;

        byte[] chunk = DataIO.readByteArray(parent);
        ByteArrayInputStream bais = new ByteArrayInputStream(chunk);
        wrapped = bais;

        if (cipher != null && secret != null) {
            wrapped = new CipherInputStream(wrapped, cipher);
        }

        magic = DataIO.readRawBytes(wrapped, SQZ.MAGIC_CHUNK_DATA.length);
        if (!Arrays.equals(magic, SQZ.MAGIC_CHUNK_DATA)) {
            throw new IOException("Invalid chunk data! Check encryption password! (Chunk #" + chunkCount + ") Magic: " + StringUtils.byteArrayToString(magic));
        }

        if (compressionType == SQZ.COMPRESS_DEFLATE) {
            wrapped = new InflaterInputStream(wrapped);
        } else if (compressionType == SQZ.COMPRESS_BZIP2) {
            wrapped = new BZip2CompressorInputStream(wrapped);
        }

        wrapped = new MessageDigestInputStream(wrapped, md);

        return true;
    }
    
    @Override
    public void close() throws IOException{
        if (closed) {
            return;
        }
        parent.close();
        closed = true;
    }


    public int getChunkCount() {
        return chunkCount;
    }
}
