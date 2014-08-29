package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;

import org.ngsutils.fastq.FastqRead;

public class SQZReader_1_1 extends SQZReader{
    public SQZReader_1_1(InputStream is, SQZHeader header, boolean includeComments, String password) throws IOException {
        super(is, header, includeComments, password);
    }

    public FastqRead nextRead() throws IOException {
        if (closed) {
            throw new IOException("Tried to read from closed file!");
        }
        if (header.paired) {
            throw new IOException("Tried to fetch one read in paired mode!");
        }

        String name = in.readString();
        if (name == null) {
            return null;
        }
        String comment = null;

        if (header.hasComments) {
            comment = in.readString();
            if (ignoreComments) {
                comment = null;
            }
        }
       
        byte[] sqbuf = in.readByteArray();
        String[] sq = SQZ.splitSeqQual(sqbuf);
        
        return new FastqRead(name, sq[0], sq[1], comment);
    }
    
    public FastqRead[] nextPair() throws IOException {
        if (closed) {
            throw new IOException("Tried to read from closed file!");
        }
        if (!header.paired) {
            throw new IOException("Tried to fetch two reads in single mode!");
        }

        String name = in.readString();
        if (name == null) {
            return null;
        }

        String comment1 = null;
        String comment2 = null;

        if (header.hasComments) {
            comment1 = in.readString();
            comment2 = in.readString();
            if (ignoreComments) {
                comment1 = null;
                comment2 = null;
            }
        }

        byte[] sqbuf1 = in.readByteArray();
        byte[] sqbuf2 = in.readByteArray();
        
        String[] sq1 = SQZ.splitSeqQual(sqbuf1);
        String[] sq2 = SQZ.splitSeqQual(sqbuf2);

        return new FastqRead[] { new FastqRead(name, sq1[0], sq1[1], comment1), new FastqRead(name, sq2[0], sq2[1], comment2)};
    }
}
