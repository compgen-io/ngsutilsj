package io.compgen.ngsutils.fasta;

import io.compgen.support.StringLineReader;
import io.compgen.support.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class IndexedFastaFile implements FastaReader {
    public class IndexRecord {
        public final String name;
        public final long length;
        public final long offset;
        public final int lineSeqLength;
        public final int lineOffsetLength;

        public IndexRecord(String name, long length, long offset, int lineSeqLength,
                int lineOffsetLength) {
            this.name = name;
            this.length = length;
            this.offset = offset;
            this.lineSeqLength = lineSeqLength;
            this.lineOffsetLength = lineOffsetLength;
        }
    }
    
    private RandomAccessFile file;

    private Map<String, IndexRecord> indexMap = new LinkedHashMap<String, IndexRecord>();
    
    public IndexedFastaFile(String filename) throws IOException {
        if (!new File(filename+".fai").exists()) {
            throw new IOException("Missing FAI index file!");
        }
        
        file = new RandomAccessFile(filename, "r");
        for (String line: new StringLineReader(filename+".fai")) {
            String[] cols = StringUtils.strip(line).split("\t");
            indexMap.put(cols[0], new IndexRecord(cols[0], Long.parseLong(cols[1]), Long.parseLong(cols[2]), Integer.parseInt(cols[3]), Integer.parseInt(cols[4])));
        }
    }
    
    public Set<String> getReferenceNames() {
        return indexMap.keySet();
    }
    
    public long getReferenceLength(String name) {
        if (indexMap.containsKey(name)) {
            return indexMap.get(name).length;
        }
        return -1; 
    }
    
    /*
     * start is zero-based
     */
    /* (non-Javadoc)
     * @see io.compgen.ngsutils.fasta.FASTAReader#fetch(java.lang.String, int, int)
     */
    @Override
    public String fetch(String ref, int start, int end) throws IOException {
        if (!indexMap.containsKey(ref)) {
            throw new RuntimeException("Invalid reference name! \""+ref+"\" not found in FASTA file!");
        }
        long pos = indexMap.get(ref).offset;
        int linenum = start / indexMap.get(ref).lineSeqLength;
        int lineoff = start % indexMap.get(ref).lineSeqLength;
        pos += linenum * indexMap.get(ref).lineOffsetLength + lineoff;
        
        String out = "";
        
        int chars = 0;
        file.seek(pos);
        while (chars < end-start) {
            out += (char)file.readByte();
            chars++;
            lineoff++;
            
            if (lineoff >= indexMap.get(ref).lineSeqLength) {
                file.skipBytes(indexMap.get(ref).lineOffsetLength - indexMap.get(ref).lineSeqLength);
                lineoff = 0;
            }
        }
        
        return out;
        
    }
    
    /* (non-Javadoc)
     * @see io.compgen.ngsutils.fasta.FASTAReader#close()
     */
    @Override
    public void close() throws IOException {
        file.close();
    }
}
