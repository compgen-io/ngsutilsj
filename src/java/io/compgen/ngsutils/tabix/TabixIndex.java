package io.compgen.ngsutils.tabix;

import java.io.IOException;
import java.util.List;

class Chunk {
    final protected long coffsetBegin;
    final protected int uoffsetBegin;
    final protected long coffsetEnd;
    final protected int uoffsetEnd;

    public Chunk(long begin, long end) {
        coffsetBegin = (begin >> 16) & 0xFFFFFFFFFFFFl;
        uoffsetBegin = (int) (begin & 0xFFFF);
        
        coffsetEnd = (end >> 16) & 0xFFFFFFFFFFFFl;
        uoffsetEnd = (int) (end & 0xFFFF);

    }
}

public interface TabixIndex {
    public List<Chunk> find(String chrom, int start, int end) throws IOException;
    public void close() throws IOException;
    public char getMeta();
    public int getColSeq();
    public int getColBegin();
    public int getColEnd();
    public int getFormat();
    public boolean containsSeq(String name);
}
