package io.compgen.ngsutils.kmer;

import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.SeqUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class KmerCounter {
    private Map<String, Long> records = new TreeMap<String, Long>(); // store kmers in sorted order
    private final int size;
    private final boolean stranded;
    private final boolean allowWildcard;
    
    public KmerCounter(int size, boolean stranded, boolean allowWildcard) {
        this.size = size;
        this.stranded = stranded;
        this.allowWildcard = allowWildcard;
    }

    public KmerCounter(int size) {
        this(size, false, false);
    }
    
    public void addRead(String seq) {
        for (int i=0; i<seq.length()-size; i++) {
            addKmer(seq.substring(i, i+size));
        }
    }

    private void addKmer(String kmer) {
        kmer = kmer.toUpperCase();
        if (!allowWildcard && kmer.indexOf('N') > -1) {
            return;
        }
        if (!stranded) {
            String revcomp = SeqUtils.revcomp(kmer);
            if (revcomp.compareTo(kmer) < 0) {
                kmer = revcomp;
            }
        }
        if (!records.containsKey(kmer)) {
            records.put(kmer,  1L);
        } else {
            records.put(kmer,  records.get(kmer) + 1);            
        }
    }
    
    public void write(OutputStream os, boolean header) throws IOException {
        if (header) {
            os.write(("##"+NGSUtils.getArgs()+"\n").getBytes());
            os.write(("#size "+size+"\n").getBytes());

            if (stranded) {
                os.write("#stranded\n".getBytes());
            }
            if (allowWildcard) {
                os.write("#wildcard\n".getBytes());
            }

        }

        Charset utf8 = Charset.forName("UTF-8");
        for (Entry<String, Long> kmer: records.entrySet()) {
            os.write((kmer.getKey()+"\t"+kmer.getValue()+"\n").getBytes(utf8));
        }
    }

    public void clear() {
        records.clear();
    }

    public void write(OutputStream out) throws IOException {
        write(out, true);        
    }
    
}
