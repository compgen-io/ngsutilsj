package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Read/write data in binary format (little-endian)
 * @author mbreese
 *
 */
public class DataIO {
    public static final String DEFAULT_ENCODING = "UTF-8";

    public static int readUint16(InputStream in) throws IOException {
        byte[] b = readRawBytes(in, 2);
        return ((b[0] & 0xFF) << 8) |
                (b[1] & 0xFF);
    }
    public static long readUint32(InputStream in) throws IOException {
        byte[] b = readRawBytes(in, 4);
        long val = 0;
        val |= (b[0] & 0xFF); val = val << 8;
        val |= (b[1] & 0xFF); val = val << 8;
        val |= (b[2] & 0xFF); val = val << 8;
        val |= (b[3] & 0xFF);
        return val & 0xFFFFFFFF;
    }
    public static long readUint64(InputStream in) throws IOException {
        byte[] b = readRawBytes(in, 8);
        long val = 0;
        val |= (b[0] & 0xFF); val = val << 8;
        val |= (b[1] & 0xFF); val = val << 8;
        val |= (b[2] & 0xFF); val = val << 8;
        val |= (b[3] & 0xFF); val = val << 8;
        val |= (b[4] & 0xFF); val = val << 8;
        val |= (b[5] & 0xFF); val = val << 8;
        val |= (b[6] & 0xFF); val = val << 8;
        val |= (b[7] & 0xFF);
        return val & 0x7FFFFFFFFFFFFFFFL;

    }

    public static long readVarInt(InputStream in) throws IOException {
        int shift = 0;
        long acc = 0;
        int tmp;
//        System.err.print("varInt: ");
        while ((tmp = in.read()) != -1) {
            byte b = (byte) (tmp & 0xFF);
//            System.err.print(String.format("%02X ", b));
            acc |= (long)(b & 0x7F) << shift;
            if (((byte) b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        
        if (shift == 0 && acc == 0) {
            return -1;
        }

//        System.err.println(acc);
        return acc;
    }

    public static byte readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) {
            return -1;
        }
        return (byte)(b & 0xFF);
    }
    
    public static String readString(InputStream in) throws IOException {
        return readString(in, DEFAULT_ENCODING);
    }
    public static String readString(InputStream in, String encoding) throws IOException {
        int size = (int) readVarInt(in);
        if (size == -1 || size == 0) {
            return null;
        }
        byte[] b = readRawBytes(in, size);
        if (b == null) {
            return null;
        }

//        System.err.println("str["+size+"] "+new String(b, encoding));

        return new String(b, encoding);
    }

    public static byte[] readByteArray(InputStream in) throws IOException {
        int size = (int) readVarInt(in);
        if (size < 0) {
            return null;
        }
        if (size == 0) {
            return new byte[0];
        }
//        System.err.println("reading byte["+size+"]");
        byte[] buf = readRawBytes(in, size);
//        System.err.println("byte["+size+"] "+StringUtils.join(" ", buf));
        return buf;
    }

    public static byte[] readRawBytes(InputStream in, int size) throws IOException {
//        System.err.println("readRawBytes(in, "+size+")");
        byte[] buf = new byte[size];
        int t = 0;
        int total = 0;
        
        while ((t = in.read(buf, total, size-total)) != -1) {
            total += t;
            if (total == size) {
//                System.err.println("byte[] "+StringUtils.join(" ", buf));
                return buf;
            }
        }
        return null;
    }

    /**
     * This isn't quite a Uint64, but rather a Uint63 - we will only write positive numbers
     * @param out
     * @param val
     * @throws IOException
     */
    public static void writeUInt64(OutputStream out, long val) throws IOException {
        if (val > 0x7FFFFFFFFFFFFFFFL) {
            throw new IOException("value is too large!");
        }
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
        
        writeRawBytes(out, b);
    }

    public static void writeUInt32(OutputStream out, long val) throws IOException {
        long v = val & 0xFFFFFFFF;
        byte[] b = new byte[4];
        
        b[3] = (byte) (v & 0xFF);
        b[2] = (byte) (v >> 8 & 0xFF);
        b[1] = (byte) (v >> 16 & 0xFF);
        b[0] = (byte) (v >> 24 & 0xFF);
        
        writeRawBytes(out, b);
    }

    public static void writeUInt16(OutputStream out, int val) throws IOException {
        int v = val & 0xFFFF;
        
        byte[] b = new byte[2];
        b[1] = (byte) (v & 0xFF);
        b[0] = (byte) (v >> 8 & 0xFF);
        
        writeRawBytes(out, b);
    }

    public static void writeString(OutputStream out, String s) throws IOException {
        writeString(out, s, DEFAULT_ENCODING);
    }
    public static void writeString(OutputStream out, String s, String encoding) throws IOException {
        if (s == null) {
            s = "";
        }
        byte[] b = s.getBytes(encoding);
        writeByteArray(out, b);
    }
    public static void writeByteArray(OutputStream out, byte[] b) throws IOException {
        writeVarInt(out, b.length);
        writeRawBytes(out, b);
    }

    public static void writeVarInt(OutputStream out, long val) throws IOException {
        long v = val & 0x7FFFFFFFFFFFFFFFL;
        
        while (v >= 0x7F) {
            writeRawByte(out, (byte)((v & 0x7F) | 0x80));
            v = v >> 7;
        }
        writeRawByte(out, (byte)(v & 0x7F));
    }

    public static void writeRawByte(OutputStream out, byte b) throws IOException {
        out.write(b); 
    }
    public static void writeRawBytes(OutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }
    public static void writeRawBytes(OutputStream out, byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }
    public static void writeByteArray(OutputStream out, byte[] buf, int off, int len) throws IOException {
        writeVarInt(out, len-off);
        writeRawBytes(out, buf, off, len);
    }

}
