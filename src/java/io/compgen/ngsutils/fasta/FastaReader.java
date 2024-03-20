package io.compgen.ngsutils.fasta;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public abstract class FastaReader {

    /*
     * start is zero-based
     */
    public abstract String fetchSequence(String ref, int start, int end) throws IOException;
    public abstract Iterator<FastaRecord> iterator() throws IOException;
    public abstract Iterator<FastaChunkRecord> iteratorChunk(int size) throws IOException;
    public abstract void close() throws IOException;
    
    public static FastaReader open(String filename) throws IOException {
        if (new File(filename+".fai").exists()) {
            return new IndexedFastaFile(filename);
        }
        
        return new BasicFastaReader(filename);

    }

}
