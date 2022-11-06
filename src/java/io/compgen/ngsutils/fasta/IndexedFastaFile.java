package io.compgen.ngsutils.fasta;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

public class IndexedFastaFile extends BasicFastaReader {
    private FAIFile fai = null;
	public RandomAccessFile file;

	public IndexedFastaFile(String filename) throws IOException {
        super(filename);
        
        if (!new File(filename+".fai").exists()) {
            throw new IOException("Missing FAI index file!");
        }
        
        this.fai = new FAIFile(filename + ".fai");        
        this.file = new RandomAccessFile(filename, "r");
    }
    
    public List<String> getReferenceNames() {
        return fai.getNames();
    }
    
    public long getReferenceLength(String name) {
    	return fai.getLength(name);
    }
    
    /*
     * start is zero-based
     */
    /* (non-Javadoc)
     * @see io.compgen.ngsutils.fasta.FASTAReader#fetch(java.lang.String, int, int)
     */
    @Override
    public String fetchSequence(String ref, int start, int end) throws IOException {
        if (!fai.contains(ref)) {
            throw new RuntimeException("Invalid reference name! \""+ref+"\" not found in FASTA file!");
        }
        
        long offset = fai.getOffset(ref);
        int lineSeqLength = fai.getLineSeqLength(ref);
        int lineOffsetLength = fai.getLineOffsetLength(ref);
        
        long pos = offset;
        int linenum = start / lineSeqLength;
        int lineoff = start % lineSeqLength;
        pos += linenum * lineOffsetLength + lineoff;
        
        String out = "";
        
        int chars = 0;
        file.seek(pos);
        while (chars < end-start) {
            out += (char)file.readByte();
            chars++;
            lineoff++;
            
            if (lineoff >= lineSeqLength) {
                file.skipBytes(lineOffsetLength - lineSeqLength);
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
