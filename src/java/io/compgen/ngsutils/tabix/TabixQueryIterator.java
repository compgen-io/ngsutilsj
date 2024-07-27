package io.compgen.ngsutils.tabix;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;

import io.compgen.common.Pair;
import io.compgen.ngsutils.tabix.BGZFile.BGZBlock;
import io.compgen.ngsutils.tabix.TabixIndex.Chunk;

public class TabixQueryIterator implements Iterator<String> {
    private final String ref;
    private final int start;
    private final int end;
    private final TabixIndex index;
    private final BGZFile bgzf;
    
    private final Deque<String> buffer = new ArrayDeque<String>();
    private final Set<Pair<Long, Integer>> linePosSet = new HashSet<Pair<Long,Integer>>();
    private final List<Chunk> chunks;
    
    public TabixQueryIterator(String ref, int start, int end, TabixIndex index, BGZFile bgzf) throws IOException, DataFormatException {
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.index = index;
        this.bgzf = bgzf;
        
        this.chunks = index.find(ref, start, end);
        
//        System.err.println("Setting up iterator for: "+ref+":"+start+"-"+end);
        
        populateChunk();
    }

    
    /*
     *  b is this record's start
     *  e is this record's end
     *  
     *  start is the query range start
     *  end is the query range end
     *
     * if the record contains the query *or overlaps the edges*, then add this line to the output
     *
     * YES:
     *      |             |
     *      |-------------|
     *      |   [query]   |
     *      [query]
     *              [query]
     * 
     * Also YES:
     * 
     *      |             |
     *      |-------------|
     *      |             |
     *                 [query]
     * [query]
     *    [ ===== query===== ]
     *    
     *    
     */

    private void populateChunk() throws IOException, DataFormatException {
        while (buffer.size() == 0) {
            if (chunks.size() == 0) {
                return;
            }
            
//            System.err.println("populate()");
            
            Chunk chunk = chunks.remove(0);
            
//            byte[] chunkBuf = bgzf.readBlocks(chunk.coffsetBegin, chunk.uoffsetBegin, chunk.coffsetEnd, chunk.uoffsetEnd);
            
            long blockPos = chunk.coffsetBegin;

            byte[] remainingBuf = null;
            long remainingCOffset = -1;
            int remainingLinePos = -1;

            while (blockPos <= chunk.coffsetEnd) {
            
                BGZBlock block = bgzf.readBlock(blockPos);
                
                int blockStart = 0;
                int blockEnd = block.uBuf.length;
                
                if (block.cPos == chunk.coffsetBegin) {
                    blockStart = chunk.uoffsetBegin;
                }
                
                if (block.cPos == chunk.coffsetEnd) {
                    blockEnd = chunk.uoffsetEnd;
                }

//                System.err.println("Reading block: " + blockPos + ", " + blockStart);

                
                byte[] uBuf = Arrays.copyOfRange(block.uBuf, blockStart, blockEnd);
                
                int uBufStartPos = 0;
                int uBufIdx = 0;
                // iterate through the buffer looking for lines (ends in \n)
                while (uBufIdx < uBuf.length) {
//                    System.err.println("["+i+"] " + new String(new byte[]{uBuf[i]},"UTF-8"));
                    if (uBuf[uBufIdx] == '\n') {
                        long lineCOffset = blockPos;
                        int lineUOffset = uBufStartPos + blockStart;

                        // pull out the UTF-8 string                    
                        String line = new String(uBuf, uBufStartPos, uBufIdx-uBufStartPos, "UTF-8");
                        
                        if (remainingBuf != null && remainingBuf.length > 0) {
//                            System.err.println("***** WRAPPING *****");
//                            System.err.println("curLine: " + line);
//                            System.err.println("remain : " + new String(remainingBuf,"UTF-8"));
                            line = new String(remainingBuf,"UTF-8") + line;
//                            System.err.println("newLine : " + line);
                            lineUOffset = remainingLinePos;
                            lineCOffset = remainingCOffset;
                            remainingBuf = null;                            
                        }
//                        System.err.println("["+lineCOffset+"," + lineUOffset+ "; "+uBufStartPos + ","+uBufIdx+"] " + line);

                        if (!line.startsWith(""+index.getMeta())) {

                            // split into columns
                            String[] cols = line.split("\t");
//                            System.err.println(StringUtils.join(" | ", cols));
                            
                            // is this the reference we are looking for?
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
                                    
                                    if (!index.isZeroBased()) {
                                        // convert one-based begin coord (in bgzip file)
                                        b--;
                                    }
                
                                    if (b > end) {
                                        // we are past the pos we need, so no more valid lines in this chunk.
//                                        System.err.println("start-pos past query end");
                                        // break out of for and while loops
                                        blockPos = chunk.coffsetEnd +1;
                                        uBufIdx = uBuf.length;
                                        break;
//                                        return;
                                    }
                                    
                                    // return if the spans overlap at all -- if necessary, the 
                                    // calling function can re-parse the line.
                                    
                                    if (
                                            (b <= start && start < e) || // query start is within tabix range
                                            (start <= b && e < end) ||   // tabix range is contained completely by query
                                            (b < end && end <= e)        // query end is within tabix range
                                        ) {
//                                        System.err.println("Chunk: " + chunk.coffsetBegin +","+ chunk.uoffsetBegin+","+ chunk.coffsetEnd+","+ chunk.uoffsetEnd);
                                        Pair<Long, Integer> key = new Pair<Long, Integer>(lineCOffset, lineUOffset);
                                        if (!linePosSet.contains(key)) {
//                                            System.err.println("Adding line to buffer: ("+lineCOffset+"," + (lineUOffset)+ ") => " + line);
                                            linePosSet.add(key);
                                            buffer.addLast(line);
                                        } else {
//                                            System.err.println("Line already returned: ("+lineCOffset+"," + (lineUOffset)+ ") => " + line);
                                        }
                                    }
                                } catch (Exception ex) {
//                                    System.err.println("ERROR chunk"+chunk.coffsetBegin +","+ chunk.uoffsetBegin +" -> "+ chunk.coffsetEnd+","+ chunk.uoffsetEnd);
//                                    System.err.println("ref="+ref+", b="+b+", e="+e+", start="+start+", end="+end);
//                                    System.err.println("line => " + line);
//                                    System.err.println("cols => " + StringUtils.join(", ", cols));
                                    ex.printStackTrace(System.err);
                                    System.exit(1);
                                }
                            }
                        }

//                        System.err.println("New uBufStart = "+(uBufIdx+1));
                        uBufStartPos = uBufIdx+1;

                    }
                    uBufIdx++;
                }
                
                remainingBuf = Arrays.copyOfRange(uBuf, uBufStartPos, uBuf.length);
//                System.err.println("setting remain: " + remainingBuf.length+", uBufIdx="+uBufIdx+", uBufStartPos="+uBufStartPos+", uBuf.length="+uBuf.length+", uBufIdx < uBuf.length?"+(uBufIdx < uBuf.length));

                remainingLinePos = uBufStartPos + blockStart;
                remainingCOffset = blockPos;
                
                blockPos += block.cLength;
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
                populateChunk();
            } catch (IOException | DataFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return next;
    }

}
    