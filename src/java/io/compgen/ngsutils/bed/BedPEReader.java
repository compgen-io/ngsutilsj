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

public class BedPEReader {
    public static Iterator<BedPERecord> readFile(String filename) throws IOException {
        return readFile(filename, false);
    }
    public static Iterator<BedPERecord> readFile(String filename, boolean ignoreStrand) throws IOException {
        if (filename.equals("-")) {
            return readInputStream(System.in, ignoreStrand);
        }
        return readFile(new File(filename), ignoreStrand);
    }

    public static Iterator<BedPERecord> readFile(File file) throws IOException {
        return readInputStream(new FileInputStream(file), false);
    }

    public static Iterator<BedPERecord> readFile(File file, boolean ignoreStrand) throws IOException {
        return readInputStream(new FileInputStream(file), ignoreStrand);
    }

    public static Iterator<BedPERecord> readInputStream(final InputStream is) throws IOException {
        return readInputStream(is, false);
    }
    public static Iterator<BedPERecord> readInputStream(final InputStream rawInput, final boolean ignoreStrand) throws IOException {
        PeekableInputStream peek = new PeekableInputStream(rawInput);
        byte[] magic = peek.peek(2);
        final InputStream is;
        if (Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B})) { // need to cast 0x8b because it is a neg. num in 2-complement
            is = new GzipCompressorInputStream(peek, true);
        } else {
            is = peek;
        }
        
        return new Iterator<BedPERecord>() {
        	BedPERecord next = null;
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
                    
                    final String chrom1 = cols[0];
                    final int start1 = Integer.parseInt(cols[1]); // file is 0-based
                    final int end1 = Integer.parseInt(cols[2]);
                    Strand strand1 = Strand.NONE;

                    final String chrom2 = cols[3];
                    final int start2 = Integer.parseInt(cols[4]); // file is 0-based
                    final int end2 = Integer.parseInt(cols[5]);
                    Strand strand2 = Strand.NONE;

                    String name = "";
                    String score = "";
                    
                    if (!ignoreStrand && cols.length > 8) {
                        if (cols[8].equals("+")) {
                            strand1 = Strand.PLUS;
                        } else if (cols[8].equals("-")) {
                            strand1 = Strand.MINUS;
                        } else {
                            // this shouldn't happen
                            strand1 = Strand.NONE;
                        }
                    }

                    if (!ignoreStrand && cols.length > 9) {
                        if (cols[9].equals("+")) {
                            strand2 = Strand.PLUS;
                        } else if (cols[9].equals("-")) {
                            strand2 = Strand.MINUS;
                        } else {
                            // this shouldn't happen
                            strand2 = Strand.NONE;
                        }
                    }

                    if (cols.length > 6) {
                        name = cols[6];
                    }
    
                    if (cols.length > 7) {
                        score = cols[7];
                    }
    
                    String[] extras = null;
                    if (cols.length > 10) {
                        extras = new String[cols.length-10];
                        for (int i=10; i<cols.length; i++) {
                            extras[i-10] = cols[i];
                        }
                    }
                    
                    GenomeSpan coord = new GenomeSpan(chrom1, start1, end1, strand1);
                    GenomeSpan coord2 = new GenomeSpan(chrom2, start2, end2, strand2);
                    next = new BedPERecord(coord, coord2, name, score, extras);
                    return;
                }
                try {
                    is.close();
                } catch (IOException e) {
                }
            }

            @Override
            public BedPERecord next() {
                if (first) {
                    loadNext();
                    first = false;
                }
                BedPERecord cur = next;
                loadNext();
                return cur;
            }

            @Override
            public void remove() {
            }};
    }
}

