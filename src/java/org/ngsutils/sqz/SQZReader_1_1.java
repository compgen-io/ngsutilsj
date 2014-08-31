package org.ngsutils.sqz;

import java.io.IOException;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.io.DataIO;

public class SQZReader_1_1 extends SQZReader{
    protected SQZReader_1_1(SQZInputStream inputStream, SQZHeader header, boolean includeComments, String password) throws IOException {
        super(inputStream, header, includeComments, password);
    }

    
    public FastqRead[] nextRead() throws IOException {
        if (closed) {
            throw new IOException("Tried to read from closed file!");
        }
        
        FastqRead[] out = new FastqRead[header.seqCount]; 
        
        String name = DataIO.readString(dataInputStream);
        if (name == null) {
            return null;
        }

        String[] comment = null;
        
        if (header.hasComments) {
            comment = new String[header.seqCount];
            for (int i=0; i<header.seqCount; i++) {
                if (ignoreComments) {
                    comment[i] = null;
                    DataIO.readString(dataInputStream);
                } else {
                    comment[i] = DataIO.readString(dataInputStream);
                }
            }
        }

        for (int i=0; i<header.seqCount; i++) {
            byte[] sqbuf = DataIO.readByteArray(dataInputStream);
            String[] sq = SQZ.splitSeqQual(sqbuf);
            out[i] = new FastqRead(name, sq[0], sq[1], (comment == null) ? null: comment[i]);
        }
        return out;
    }
}
