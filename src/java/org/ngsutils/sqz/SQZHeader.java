package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.ngsutils.support.io.DataInput;
import org.ngsutils.support.io.DataOutput;

public class SQZHeader {
    public final boolean deflate;
    public final boolean paired;
    public final boolean hasComments;
    public final boolean colorspace;
    public final boolean colorspacePrefix;
    
    public final int major;
    public final int minor;

    public final int flags;
    
    public final String encryption;

    public SQZHeader(int major, int minor, int flags, String encryption) {
        this.major = major;
        this.minor = minor;
        this.flags = flags;
        this.encryption = encryption;
        
        this.deflate = (flags & SQZ.DEFLATE_COMPRESSED) > 0;
        this.paired = (flags & SQZ.PAIRED) > 0;
        this.hasComments = (flags & SQZ.HAS_COMMENTS) > 0;
        this.colorspace = (flags & SQZ.COLORSPACE) > 0;
        this.colorspacePrefix = (flags & SQZ.COLORSPACE_PREFIX) > 0;
    }
    
    public void writeHeader(OutputStream os) throws IOException {
        DataOutput out = new DataOutput(os);
        out.writeRawBytes(SQZ.MAGIC); // 4 bytes
        out.writeUInt16(major);       // 2 bytes
        out.writeUInt16(minor);       // 2 bytes

        out.writeUInt32(flags);       // 4 bytes
        out.writeString(encryption); // encryption string (varint + string)

    }
    
    public static SQZHeader readHeader(InputStream is) throws IOException {
        DataInput in = new DataInput(is);
        byte[] sigbuf = in.readRawBytes(SQZ.MAGIC.length);
        
        if (!Arrays.equals(SQZ.MAGIC, sigbuf)) {
            throw new IOException("Invalid SQZ file! Invalid magic bytes!");
        }

        int major = in.readUint16();
        int minor = in.readUint16();
        
        int flags = (int) in.readUint32();
        String encryption = in.readString();
        
        return new SQZHeader(major, minor, flags, encryption);

    }    
}
