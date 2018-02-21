package io.compgen.ngsutils.tabix;

import java.io.IOException;
import java.util.List;

class Chunk {
    final protected long begin;
    final protected long end;
    
    final protected int coffsetBegin;
    final protected int uoffsetBegin;
    final protected int coffsetEnd;
    final protected int uoffsetEnd;

    public Chunk(long begin, long end) {
        this.begin = begin;
        this.end = end;

        coffsetBegin = (int) (begin>>16);
        uoffsetBegin = (int) (begin & 0xFFFF);
        
        coffsetEnd = (int) (end>>16);
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
}
