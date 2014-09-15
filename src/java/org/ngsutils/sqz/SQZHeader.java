package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.ngsutils.support.io.DataIO;

public class SQZHeader {
    public final boolean hasComments;
    public final boolean colorspace;
    
    public final int major;
    public final int minor;
    public final int flags;
    public final long timestamp;
    public final int seqCount;
    
    public final int compressionType;
    
    public final String encryption;

    public SQZHeader(int major, int minor, int flags, int seqCount, int compressionType, String encryption, long timestamp) {
        this.major = major;
        this.minor = minor;
        this.flags = flags;
        this.seqCount = seqCount;
        this.compressionType = compressionType;
        this.encryption = encryption;
        
        this.hasComments = (flags & SQZ.HAS_COMMENTS) > 0;
        this.colorspace = (flags & SQZ.COLORSPACE) > 0;
        
        this.timestamp = timestamp;
    }

    public SQZHeader(int major, int minor, int flags, int seqCount, int compressionType, String encryption) {
        this(major, minor, flags, seqCount, compressionType, encryption, System.currentTimeMillis() / 1000L);
    }
    
    public void writeHeader(OutputStream os) throws IOException {
        DataIO.writeRawBytes(os, SQZ.MAGIC); // 4 bytes
        DataIO.writeUInt16(os, major);       // 2 bytes
        DataIO.writeUInt16(os, minor);       // 2 bytes
        DataIO.writeUInt32(os, flags);       // 4 bytes
        DataIO.writeUInt64(os, timestamp);  // 8 bytes
        DataIO.writeRawByte(os, (byte) (seqCount & 0xFF));  // 1 byte
        DataIO.writeRawByte(os, (byte) (this.compressionType & 0xFF));
        
        DataIO.writeString(os, encryption); // encryption string (varint + string)

    }
    
    public static SQZHeader readHeader(InputStream is) throws IOException {
        byte[] sigbuf = DataIO.readRawBytes(is, SQZ.MAGIC.length);
        
        if (!Arrays.equals(SQZ.MAGIC, sigbuf)) {
            throw new IOException("Invalid SQZ file! Invalid magic bytes!");
        }

        int major = DataIO.readUint16(is);
        int minor = DataIO.readUint16(is);
        int flags = (int) DataIO.readUint32(is);
        long timestamp = DataIO.readUint64(is);
        int seqCount = DataIO.readByte(is);
        
        int compressionType = DataIO.readByte(is);
        String encryption = DataIO.readString(is);
        
        return new SQZHeader(major, minor, flags, seqCount, compressionType, encryption, timestamp);

    }    
}
