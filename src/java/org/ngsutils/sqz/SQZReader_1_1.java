package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public class SQZReader_1_1 extends SQZReader{
    protected SQZReader_1_1(InputStream inputStream, SQZHeader header, boolean includeComments, String password, boolean verbose) throws IOException, GeneralSecurityException {
        super(inputStream, header, includeComments, password, verbose);
    }

    
    public FastqRead[] nextRead() throws IOException {
        if (closed) {
            throw new IOException("Tried to read from closed file!");
        }
        
        FastqRead[] out = new FastqRead[header.seqCount]; 
        
        String name = DataIO.readString(dcis);
        if (name == null) {
            return null;
        }

        String[] comment = null;
        
        if (header.hasComments) {
            comment = new String[header.seqCount];
            for (int i=0; i<header.seqCount; i++) {
                if (ignoreComments) {
                    comment[i] = null;
                    DataIO.readString(dcis);
                } else {
                    comment[i] = DataIO.readString(dcis);
                }
            }
        }

        for (int i=0; i<header.seqCount; i++) {
            byte[] sqbuf = DataIO.readByteArray(dcis);
            String[] sq = SQZ.splitSeqQual(sqbuf);
            out[i] = new FastqRead(name, sq[0], sq[1], (comment == null) ? null: comment[i]);
        }
        return out;
    }
}
