package org.ngsutils.sqz;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public class SQZWriter {
    public static final int MAJOR = 1;
    public static final int MINOR = 1;

    protected SQZOutputStream sqzos;
    protected OutputStream wrappedOutputStream;
    protected boolean closed = false;
    
    public final int flags;
    public final SQZHeader header;
    
    public SQZWriter(OutputStream os, int flags, int seqCount, String encryption, String password) throws IOException, NoSuchAlgorithmException {
        sqzos = new SQZOutputStream(os);
        
        this.flags = flags;
        header = new SQZHeader(MAJOR, MINOR, flags, seqCount, encryption);
        header.writeHeader(sqzos);

        if (header.deflate) {
            wrappedOutputStream = new DeflaterOutputStream(sqzos);
        } else {
            wrappedOutputStream = new BufferedOutputStream(sqzos);
        }
    }

    public SQZWriter(OutputStream out, int flags, int seqCount) throws IOException, NoSuchAlgorithmException {
        this(out, flags, seqCount, null, null);
    }

    public SQZWriter(String filename, int flags, int seqCount, String encryptionAlgorithm, String password) throws IOException, NoSuchAlgorithmException {
        this(new FileOutputStream(filename), flags, seqCount, encryptionAlgorithm, password);
    }

    public SQZWriter(String filename, int flags, int seqCount) throws IOException, NoSuchAlgorithmException {
        this(new FileOutputStream(filename), flags, seqCount);
    }
    
    public void close() throws IOException {
        if (!closed) {
            wrappedOutputStream.close();
            closed = true;
        }
    }
    
    public void writeReads(List<FastqRead> reads) throws IOException {
        if (closed) {
            throw new IOException("Tried to write to closed file!");
        }
        
        if (reads.size() != header.seqCount) {
            throw new IOException("Each record must have " + header.seqCount + " reads!");            
        }
        
        for (int i=1; i<reads.size(); i++) {
            if (!reads.get(i).getName().equals(reads.get(0).getName())) {
                throw new IOException("Reads must have the same name!");
            }
        }

        DataIO.writeString(wrappedOutputStream, reads.get(0).getName());

        if (header.hasComments) {
            for (FastqRead read: reads) {
                if (read.getComment() == null) {
                    DataIO.writeString(wrappedOutputStream, "");
                } else {
                    DataIO.writeString(wrappedOutputStream, read.getComment());
                }
            }
        }

        for (FastqRead read: reads) {
            DataIO.writeByteArray(wrappedOutputStream, SQZ.combineSeqQual(read.getSeq(), read.getQual()));
        }
    }
    
    public byte[] getDigest() throws IOException {
        return sqzos.getDigest();
    }
}
