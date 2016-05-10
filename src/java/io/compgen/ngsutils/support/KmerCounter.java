package io.compgen.ngsutils.support;

import io.compgen.common.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class KmerCounter {
    public class KmerRecord {
        private long count = 0;
        private Set<String> names = new HashSet<String>();
        
        public void addRead(String name) {
            names.add(name);
            count++;
        }
        
        public long getCount() {
            return count;
        }
        
        public Set<String> getReadNames() {
            return Collections.unmodifiableSet(names);
        }
        
        public String toString() {
            return count + "\t" + StringUtils.join("\t", names);
        }
    }
    
    private Map<String, KmerRecord> records = new TreeMap<String, KmerRecord>();
    private final int size;
    
    public KmerCounter(int size) {
        this.size = size;
    }
    
    public KmerCounter() {
        this(25);
    }
    
    public void addRead(String readName, String seq) {
        for (int i=0; i<seq.length()-size; i++) {
            addKmer(readName, seq.substring(i, i+size));
        }
    }

    private void addKmer(String readName, String kmer) {
        if (!records.containsKey(kmer)) {
            records.put(kmer, new KmerRecord());
        }
        records.get(kmer).addRead(readName);
    }
    
    public void write(OutputStream os) throws IOException {
//        int i=0;
        for (String kmer: records.keySet()) {
//            i++;
//            System.err.println("writing: "+i+"/"+records.size()+" "+kmer+ " / " + records.get(kmer).names.size());
//            String val = kmer + "\t" + records.get(kmer).count+"\t"+StringUtils.join("\t", records.get(kmer).names)+"\n";
            os.write((kmer+"\t").getBytes(Charset.forName("UTF-8")));
            os.write((records.get(kmer).count+"").getBytes(Charset.forName("UTF-8")));
            for (String name: records.get(kmer).names) {
                os.write(("\t"+name).getBytes(Charset.forName("UTF-8")));
            }
            os.write("\n".getBytes(Charset.forName("UTF-8")));
        }
    }

    public void clear() {
        records.clear();
    }
    
}
