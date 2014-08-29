package org.ngsutils.support.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Write data in binary format (little-endian)
 * @author mbreese
 *
 */
public class DataOutput {
    private OutputStream out;
    private String encoding = "UTF-8";
    
    public DataOutput(OutputStream out) {
        this.out = out;
    }

    public DataOutput(OutputStream out, String encoding) {
        this.out = out;
        this.encoding = encoding;
    }

    public void close() throws IOException {
        out.close();
    }

    public void writeUInt64(long val) throws IOException {
        long v = val & 0x7FFFFFFFFFFFFFFFL;
        byte[] b = new byte[8];
        
        b[7] = (byte) (v & 0xFF);
        b[6] = (byte) ((v >> 8) & 0xFF);
        b[5] = (byte) ((v >> 16) & 0xFF);
        b[4] = (byte) ((v >> 24) & 0xFF);
        b[3] = (byte) ((v >> 32) & 0xFF);
        b[2] = (byte) ((v >> 40) & 0xFF);
        b[1] = (byte) ((v >> 48) & 0xFF);
        b[0] = (byte) ((v >> 56) & 0xFF);
        
        out.write(b);
    }

    public void writeUInt32(long val) throws IOException {
        long v = val & 0xFFFFFFFF;
        byte[] b = new byte[4];
        
        b[3] = (byte) (v & 0xFF);
        b[2] = (byte) (v >> 8 & 0xFF);
        b[1] = (byte) (v >> 16 & 0xFF);
        b[0] = (byte) (v >> 24 & 0xFF);
        
        out.write(b);
    }

    public void writeUInt16(int val) throws IOException {
        int v = val & 0xFFFF;
        
        byte[] b = new byte[2];
        b[1] = (byte) (v & 0xFF);
        b[0] = (byte) (v >> 8 & 0xFF);
        
        out.write(b);
    }

    public void writeString(String s) throws IOException {
        if (s == null) {
            s = "";
        }
        byte[] b = s.getBytes(encoding);
        writeByteArray(b);
    }
    
    public void writeByteArray(byte[] b) throws IOException {
        writeVarInt(b.length);
        writeRawBytes(b);
    }

    public void writeVarInt(long val) throws IOException {
        long v = val & 0x7FFFFFFFFFFFFFFFL;
        
        while (v >= 0x7F) {
            writeRawByte((byte)((v & 0x7F) | 0x80));
            v = v >> 7;
        }
        writeRawByte((byte)(v & 0x7F));
    }

    public void writeRawByte(byte b) throws IOException {
        out.write(b);
    }
    public void writeRawBytes(byte[] bytes) throws IOException {
        out.write(bytes);
    }
}
