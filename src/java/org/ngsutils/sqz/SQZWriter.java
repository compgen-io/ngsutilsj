package org.ngsutils.sqz;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataOutput;
import org.ngsutils.support.io.SHA1OutputStream;

public class SQZWriter {
    public static final int MAJOR = 1;
    public static final int MINOR = 1;

    protected DataOutput out;
    protected boolean closed = false;
    
    public final int flags;
    public final SQZHeader header;
    
    public SQZWriter(OutputStream os, int flags, String encryption, String password) throws IOException, NoSuchAlgorithmException {
        OutputStream wrapped = new SHA1OutputStream(os);
        
        this.flags = flags;
        header = new SQZHeader(MAJOR, MINOR, flags, encryption);
        header.writeHeader(wrapped);

        if (header.deflate) {
            wrapped = new DeflaterOutputStream(wrapped);
        } else {
            wrapped = new BufferedOutputStream(wrapped);
        }
        
        this.out = new DataOutput(wrapped);
    }

    public SQZWriter(OutputStream out, int flags) throws IOException, NoSuchAlgorithmException {
        this(out, flags, null, null);
    }

    public SQZWriter(String filename, int flags, String encryptionAlgorithm, String password) throws IOException, NoSuchAlgorithmException {
        this(new FileOutputStream(filename), flags, encryptionAlgorithm, password);
    }

    public SQZWriter(String filename, int flags) throws IOException, NoSuchAlgorithmException {
        this(new FileOutputStream(filename), flags);
    }
    
    public void close() throws IOException {
        closed = true;
        this.out.close();
    }
    
    public void write(FastqRead read) throws IOException {
        if (closed) {
            throw new IOException("Tried to write to closed file!");
        }
        if (header.paired) {
            throw new IOException("Tried to write one read in paired mode!");
        }

        out.writeString(read.getName());

        if (header.hasComments) {
            if (read.getComment() == null) {
                out.writeString("");
            } else {
                out.writeString(read.getComment());
            }
        }

        out.writeByteArray(SQZ.combineSeqQual(read.getSeq(), read.getQual()));
    }

    public void writePair(FastqRead one, FastqRead two) throws IOException {
        if (closed) {
            throw new IOException("Tried to write to closed file!");
        }
        if (!header.paired) {
            throw new IOException("Tried to write two reads in single mode!");
        }
        if (!one.getName().equals(two.getName())) {
            throw new IOException("Reads must have the same name!");
        }

        out.writeString(one.getName());

        if (header.hasComments) {
            if (one.getComment() == null) {
                out.writeString("");
            } else {
                out.writeString(one.getComment());
            }
            if (two.getComment() == null) {
                out.writeString("");
            } else {
                out.writeString(two.getComment());
            }
        }

        out.writeByteArray(SQZ.combineSeqQual(one.getSeq(), one.getQual()));
        out.writeByteArray(SQZ.combineSeqQual(two.getSeq(), two.getQual()));
    }
}
