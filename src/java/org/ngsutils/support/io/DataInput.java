package org.ngsutils.support.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Read data in binary format (little-endian)
 * @author mbreese
 *
 */
public class DataInput {
    private InputStream in;
    private String encoding = "UTF-8";

    public DataInput(InputStream in) {
        this.in = in;
    }

    public DataInput(InputStream in, String encoding) {
        this.in = in;
        this.encoding = encoding;
    }

    public void close() throws IOException {
        in.close();
    }

    public int readUint16() throws IOException {
        byte[] b = readRawBytes(2);
        return ((b[0] & 0xFF) << 8) |
                (b[1] & 0xFF);
    }
    public long readUint32() throws IOException {
        byte[] b = readRawBytes(4);
        long val = 0;
        val |= (b[0] & 0xFF); val = val << 8;
        val |= (b[1] & 0xFF); val = val << 8;
        val |= (b[2] & 0xFF); val = val << 8;
        val |= (b[3] & 0xFF);
        return val & 0xFFFFFFFF;
    }
    public long readUint64() throws IOException {
        byte[] b = readRawBytes(8);
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

    public long readVarInt() throws IOException {
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

    public byte readByte() throws IOException {
        int b = in.read();
        if (b == -1) {
            return -1;
        }
        return (byte)(b & 0xFF);
    }
    
    public String readString() throws IOException {
        int size = (int) readVarInt();
        if (size == -1) {
            return null;
        }
        byte[] b = readRawBytes(size);
        if (b == null) {
//            System.err.println("str["+size+"] \"\"");
            return null;
        }

//        System.err.println("str["+size+"] "+new String(b, encoding));

        return new String(b, encoding);
    }

    public byte[] readByteArray() throws IOException {
        int size = (int) readVarInt();
        byte[] buf = readRawBytes(size);
//        System.err.println("byte["+size+"] "+StringUtils.join(" ", buf));
        return buf;
    }

    public byte[] readRawBytes(int size) throws IOException {
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

}
