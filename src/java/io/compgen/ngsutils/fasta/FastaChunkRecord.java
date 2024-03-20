package io.compgen.ngsutils.fasta;

public class FastaChunkRecord {
    public final String name;
    public final String comment;
    public final String seq;
    public final int pos; // zero-based
    
    public FastaChunkRecord(String name, String seq, int pos) {
        this(name, seq, pos, null);
    }
    
    public FastaChunkRecord(String name, String seq, int pos, String comment) {
        this.name = name;
        this.seq = seq;
        this.comment = comment;
        this.pos = pos;
    }
    
    public String toString() {
        return ">"+name + " ("+pos+"-"+(pos + seq.length())+ ")";
    }
}
