package io.compgen.ngsutils.kmer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import io.compgen.common.AbstractLineReader;

public class KmerLineReader extends AbstractLineReader<KmerRecord> {
    
    public KmerLineReader(File file) throws IOException {
        this(new FileInputStream(file), file.getAbsolutePath());
    }

    private KmerLineReader(FileInputStream fis, String fname) throws IOException {
        super(new GZIPInputStream(fis), fis.getChannel(), fname);
    }

    @Override
    protected KmerRecord convertLine(String line) {
        // if there is a comment, avoid it (shouldn't happen)
        if (line.length()>0 && line.charAt(0) == '#') {
            return null;
        }
        
        String[] vals = line.split("\t");
        String kmer = vals[0];
        long count = Long.parseLong(vals[1]);
        KmerRecord ret = new KmerRecord(kmer, count);
        return ret;
    }    
}
