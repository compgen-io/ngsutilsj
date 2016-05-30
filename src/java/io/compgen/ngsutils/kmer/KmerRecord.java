package io.compgen.ngsutils.kmer;

import java.io.IOException;
import java.io.OutputStream;

public class KmerRecord {
    public String kmer;
    public long count;

    public KmerRecord(String kmer, long count) {
        this.kmer = kmer;
        this.count = count;
    }
    
    public long getCount() {
        return count;
    }

    public String getKmer() {
        return kmer;
    }
    
    public void merge(KmerRecord other) {
        this.count += other.count;
    }
    public void write(OutputStream os) throws IOException {
        os.write((kmer+"\t"+count+"\n").getBytes());
    }
}