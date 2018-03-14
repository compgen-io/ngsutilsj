package io.compgen.ngsutils.tabix;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixIndex.Chunk;

public class TabixQueryIterator implements Iterator<String> {
    private final String ref;
    private final int start;
    private final int end;
    private final TabixIndex index;
    private final BGZFile bgzf;
    
    private final Deque<String> buffer = new ArrayDeque<String>();
    private final List<Chunk> chunks;
    
    public TabixQueryIterator(String ref, int start, int end, TabixIndex index, BGZFile bgzf) throws IOException, DataFormatException {
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.index = index;
        this.bgzf = bgzf;
        
        this.chunks = index.find(ref, start, end);
        
        populate();
    }

    private void populate() throws IOException, DataFormatException {
        if (chunks.size() == 0) {
            return;
        }
        
        Chunk chunk = chunks.remove(0);
        
        byte[] chunkBuf = bgzf.readBlocks(chunk.coffsetBegin, chunk.uoffsetBegin, chunk.coffsetEnd, chunk.uoffsetEnd);
        String s = new String(chunkBuf, "UTF-8");
        for (String line: s.split("\n")) {
            if (line.startsWith(""+index.getMeta())) {
                continue;
            }
            String[] cols = line.split("\t");
//              System.err.println(StringUtils.join(",", cols));
            if (cols[index.getColSeq()-1].equals(ref)) {
                int b = -1, e = -1;
                try {
                    if (index.getColEnd() > 0) {
                        b = Integer.parseInt(cols[index.getColBegin()-1]);
                        e = Integer.parseInt(cols[index.getColEnd()-1]);
                    } else {
                        b = Integer.parseInt(cols[index.getColBegin()-1]);
                        e = Integer.parseInt(cols[index.getColBegin()-1]);;
                    }
                    
                    if ((index.getFormat()&0x10000) == 0) {
                        // convert one-based begin coord (in bgzip file)
                        b--;
                    }

                    if (b > end) {
                        // we are past the pos we need, so no more valid lines
                        break;
                    }
                    
                    // return if the spans overlap at all -- if necessary, the 
                    // calling function can re-parse.
    
                    if (
                            (b <= start && start < e) || // query start is within tabix range
                            (start <= b && e < end) ||   // tabix range is contained completely by query
                            (b < end && end <= e)        // query end is within tabix range
                        ) {
                        buffer.addLast(line);     
                    }
                } catch (Exception ex) {
                    System.err.println("ERROR chunk"+chunk.coffsetBegin +","+ chunk.uoffsetBegin +" -> "+ chunk.coffsetEnd+","+ chunk.uoffsetEnd);
                    System.err.println("ref="+ref+", b="+b+", e="+e+", start="+start+", end="+end);
                    System.err.println("chunkBuf.length => " + chunkBuf.length);
                    System.err.println("line => " + line);
                    System.err.println("cols => " + StringUtils.join(", ", cols));
//                        for (String l: s.split("\n")) {
//                            System.err.println("s => " + l);
//                        }
                    ex.printStackTrace(System.err);
                    System.exit(1);
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        return buffer.size() > 0;
    }

    @Override
    public String next() {
        String next = buffer.pop();
        if (buffer.size() == 0) {
            try {
                populate();
            } catch (IOException | DataFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return next;
    }

}
