package io.compgen.ngsutils.bed;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.io.PeekableInputStream;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

public class BedReader {
    public static Iterator<BedRecord> readFile(String filename) throws IOException {
        return readFile(filename, false);
    }
    public static Iterator<BedRecord> readFile(String filename, boolean ignoreStrand) throws IOException {
        if (filename.equals("-")) {
            return readInputStream(System.in, ignoreStrand);
        }
        return readFile(new File(filename), ignoreStrand);
    }

    public static Iterator<BedRecord> readFile(File file) throws IOException {
        return readInputStream(new FileInputStream(file), false);
    }

    public static Iterator<BedRecord> readFile(File file, boolean ignoreStrand) throws IOException {
        return readInputStream(new FileInputStream(file), ignoreStrand);
    }

    public static Iterator<BedRecord> readInputStream(final InputStream is) throws IOException {
        return readInputStream(is, false);
    }
    public static Iterator<BedRecord> readInputStream(final InputStream rawInput, final boolean ignoreStrand) throws IOException {
        PeekableInputStream peek = new PeekableInputStream(rawInput);

        final InputStream is;
        try {
        	byte[] magic = peek.peek(2);
	        if (Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B})) { // need to cast 0x8b because it is a neg. num in 2-complement
	            is = new GzipCompressorInputStream(peek, true);
	        } else {
	            is = peek;
	        }
        }  catch (Exception e) {
        	// empty input file
            return new Iterator<BedRecord> () {

				@Override
				public boolean hasNext() {
					return false;
				}

				@Override
				public BedRecord next() {
					return null;
				}};
        }        

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
                    
                    if (!ignoreStrand && cols.length > 5) {
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
                next = null; // this needed to avoid blank lines at the end returning the last record twice
                loadNext();
                return cur;
            }

            @Override
            public void remove() {
            }};
    }
}

