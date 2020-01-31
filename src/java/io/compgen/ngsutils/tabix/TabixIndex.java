package io.compgen.ngsutils.tabix;

import java.io.IOException;
import java.util.List;



public interface TabixIndex {
    public class Chunk {
        final protected long coffsetBegin;
        final protected int uoffsetBegin;
        final protected long coffsetEnd;
        final protected int uoffsetEnd;

        public Chunk(long begin, long end) {
            coffsetBegin = (begin >> 16) & 0xFFFFFFFFFFFFL;
            uoffsetBegin = (int) (begin & 0xFFFF);
            
            coffsetEnd = (end >> 16) & 0xFFFFFFFFFFFFL;
            uoffsetEnd = (int) (end & 0xFFFF);

        }
    }
    
    public List<Chunk> find(String chrom, int start, int end) throws IOException;
    public void close() throws IOException;
    public char getMeta();
    public int getColSeq();
    public int getColBegin();
    public int getColEnd();
    public int getFormat();
    public boolean containsSeq(String name);
    public void dump() throws IOException;
    public int getSkipLines();
    
    public boolean isZeroBased();
}
