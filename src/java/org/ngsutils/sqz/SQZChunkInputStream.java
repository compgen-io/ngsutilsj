package org.ngsutils.sqz;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.io.DataIO;

public class SQZChunkInputStream extends InputStream {
    private final InputStream parent;
    private final Cipher cipher;
    private final SecretKeySpec secret;
    private final int compressionType;
    private final int ivLen;
    private final MessageDigest md;
    private boolean verbose = false;
    
    private InputStream wrapped = null;;

    private byte[] buffer = new byte[8192];
    private byte[] chunkDigest = null;
    private int pos = 0;
    private int buflen = 0;
    private boolean closed = false;
    private int chunkCount = 0;
    private Map<String, String> text = new HashMap<String, String>();
    
    public SQZChunkInputStream(InputStream parent, int compressionType, Cipher cipher, SecretKeySpec secret, int ivLen, boolean verbose) throws NoSuchAlgorithmException, IOException {
        this.parent = parent;
        this.cipher = cipher;
        this.secret = secret;
        this.ivLen = ivLen;
        this.compressionType = compressionType;
        this.md = MessageDigest.getInstance("SHA-1");
        this.verbose = verbose;
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
                    if (verbose) {
                        System.err.println("Block: "+chunkCount+" SHA-1 Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(chunkDigest));
                    }
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
    
    public void readAllChunks() throws IOException {
        while (readChunk()) {
        }
    }
    
    /**
     * Chunk format:                     
     * +--------+----------+----------+--------+=================+
     * | magic1 | raw-sha1 | comp_len | magic2 | compressed data |
     * +--------+----------+----------+--------+=================+
     * 
     * Encrypted version
     *                                     |-------- encrypted -------|       
     * +--------+----------+----+----------+--------+=================+
     * | magic1 | raw-sha1 | IV | comp_len | magic2 | compressed data |
     * +--------+----------+----+----------+--------+=================+
     * 
     * [magic1] is the SQZ.MAGIC_CHUNK
     * [magic2] is either SQZ.MAGIC_DATA_CHUNK or SQZ.MAGIC_TEXT_CHUNK
     * [comp_len] is the compressed data length
     * 
     */
    
    protected boolean readChunk() throws IOException {
        return readChunk(false);
    }
    
    protected boolean readChunk(boolean nomagic) throws IOException {
        byte[] magic = DataIO.readRawBytes(parent, SQZ.MAGIC_CHUNK.length);
        if (magic == null) {
            return false;
        }

        boolean chunkText = false;
        chunkCount ++;
        
        if (Arrays.equals(magic, SQZ.MAGIC_CHUNK)) {
            chunkText = false;
        } else if (Arrays.equals(magic, SQZ.MAGIC_TEXT_CHUNK)) {
            chunkText = true;
        } else {
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

        wrapped = new DigestInputStream(wrapped, md);
        
        if (chunkText) {
            String name = DataIO.readString(wrapped);
            String text = DataIO.readString(wrapped);
            
            this.text.put(name,  text);
            
            byte[] digest = md.digest();
            if (verbose) {
                System.err.println("Text block: "+chunkCount+" SHA-1 Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(chunkDigest));
            }
            if (!Arrays.equals(chunkDigest, digest)) {
                throw new IOException("Invalid SHA-1 signature for block "+chunkCount+" Got: "+StringUtils.byteArrayToString(digest)+" Expected:"+StringUtils.byteArrayToString(chunkDigest));
            }
            return readChunk();
        }

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

    public String getText(String name) {
        return text.get(name);
    }

    public Set<String> getTextNames() {
        return Collections.unmodifiableSet(text.keySet());
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
