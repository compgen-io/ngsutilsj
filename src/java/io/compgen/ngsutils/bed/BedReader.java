package io.compgen.ngsutils.bed;

import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class BedReader {
    public static Iterator<BedRecord> readFile(String filename) throws IOException {
        if (filename.equals("-")) {
            return readInputStream(System.in);
        }
        return readFile(new File(filename));
    }

    public static Iterator<BedRecord> readFile(File file) throws IOException {
        return readInputStream(new FileInputStream(file));
    }

    public static Iterator<BedRecord> readInputStream(final InputStream is) throws IOException {
        return new Iterator<BedRecord>() {
            BedRecord next = null;
            boolean first = true;
            
            Iterator<String> it = new StringLineReader(is).iterator();
            
            @Override
            public boolean hasNext() {
                if (first) {
                    loadNext();
                    first = false;
                }
                return next != null;
            }

            private void loadNext() {
                if (!it.hasNext()) {
                    next = null;
                    return;
                }
                
                while (it.hasNext()) {
                    String line = it.next();
                    if (line == null || line.length() == 0 || line.charAt(0) == '#') {
                        continue;
                    }
    
                    final String[] cols = StringUtils.strip(line).split("\t", -1);
                    if (cols.length < 3) {
                        continue;
                    }
                    
                    final String chrom = cols[0];
                    final int start = Integer.parseInt(cols[1]); // file is 0-based
                    final int end = Integer.parseInt(cols[2]);
                    Strand strand = Strand.NONE;
                    String name = "";
                    double score = 0;
                    
                    if (cols.length > 5) {
                        if (cols[5].equals("+")) {
                            strand = Strand.PLUS;
                        } else if (cols[5].equals("-")) {
                            strand = Strand.MINUS;
                        } else {
                            // this shouldn't happen
                            strand = Strand.NONE;
                        }
                    }
    
                    if (cols.length > 3) {
                        name = cols[3];
                    }
    
                    if (cols.length > 4) {
                        score = Double.parseDouble(cols[4]);
                    }
    
                    String[] extras = null;
                    if (cols.length > 6) {
                        extras = new String[cols.length-6];
                        for (int i=6; i<cols.length; i++) {
                            extras[i-6] = cols[i];
                        }
                    }
                    
                    GenomeSpan coord = new GenomeSpan(chrom, start, end, strand);
                    next = new BedRecord(coord, name, score, extras);
                    return;
                }
                try {
                    is.close();
                } catch (IOException e) {
                }
            }

            @Override
            public BedRecord next() {
                if (first) {
                    loadNext();
                    first = false;
                }
                BedRecord cur = next;
                loadNext();
                return cur;
            }

            @Override
            public void remove() {
            }};
    }
}

