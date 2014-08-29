package org.ngsutils.support.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionOutputStream extends OutputStream {
    private OutputStream parent;
    private boolean closed = false;
    private byte[] hash = null;
    
    private final Cipher cipher;
    
    public EncryptionOutputStream(OutputStream parent, String algorithm, byte[] salt, String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidParameterSpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
        cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
//        AlgorithmParameters params = cipher.getParameters();
//        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
    }

    @Override
    public void write(int b) throws IOException {
        byte[] ciphertext = cipher.update(new byte[]{(byte) (b & 0xFF)});
        if (ciphertext != null) {
            parent.write(ciphertext);
        }
    }

    @Override
    public void close() throws IOException {
        byte[] ciphertext;
        try {
            ciphertext = cipher.doFinal();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IOException(e);
        }
        if (ciphertext != null) {
            parent.write(ciphertext);
        }

        parent.close();
        closed = true;
    }
    
    public byte[] getMD5() {
        if (closed) {
            return hash;
        }
        return null;
    }
}
