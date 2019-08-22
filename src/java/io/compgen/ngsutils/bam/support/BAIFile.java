package io.compgen.ngsutils.bam.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.compgen.common.io.DataIO;

public class BAIFile {

    protected BAIFile(InputStream is) throws IOException {
        byte[] magic = DataIO.readRawBytes(is, 4);
        assert magic[0] == 'B';
        assert magic[1] == 'A';
        assert magic[2] == 'I';
        assert magic[3] == 1;

        int nRef = DataIO.readInt32(is);
        
        System.out.println("n_ref: " + nRef);
        
        for (int i=0; i<nRef; i++) {
            System.out.println("  [ref " + i + "]");
            int nBin = DataIO.readInt32(is);
            System.out.println("  n_bin: " + nBin);
            
            for (int j=0; j<nBin; j++) {
                long bin = DataIO.readUint32(is);
                int nChunk = DataIO.readInt32(is);
                System.out.println("    [bin " + j + ", bin: " + bin + ", n_chunk: " + nChunk+"]");

                if (bin == 37450) {
                    long begin = DataIO.readUint64(is);
                    long end = DataIO.readUint64(is);
                    long mapped = DataIO.readUint64(is);
                    long unmapped = DataIO.readUint64(is);
                    System.out.println("      [magic bin unmapped_begin="+ begin + ", unmapped_end="+ end +", n_mapped="+ mapped + ", n_unmapped="+ unmapped +"]");
                } else {
                    for (int k=0; k<nChunk; k++) {
                        long begin = DataIO.readUint64(is);
                        long end = DataIO.readUint64(is);
        //                System.out.println("      [chunk " + k + ", begin="+ begin + ", end="+ end +"]");
                    }
                }
            }
            int nIntv = DataIO.readInt32(is);
            System.out.println("  n_intv: " + nIntv);
            for (int j=0; j<nIntv; j++) {
                long ioffset = DataIO.readUint64(is);
                System.out.println("    [intv " + j + ", ioffset="+ioffset+"]");
            }

            
        }
        long nNoCoord = DataIO.readUint64(is);
        System.out.println("[n_no_coor="+nNoCoord+ "]");

    }

    public BAIFile(String filename) throws IOException {
        this(new FileInputStream(filename));
    }

    public BAIFile(File file) throws IOException {
        this(new FileInputStream(file));
    }

}
