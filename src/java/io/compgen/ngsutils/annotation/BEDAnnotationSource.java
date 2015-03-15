package io.compgen.ngsutils.annotation;

import io.compgen.ngsutils.annotation.BEDAnnotationSource.BEDAnnotation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.support.StringLineReader;
import io.compgen.ngsutils.support.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads annotations from a RepeatMasker output
 * 
 * @author mbreese
 * 
 */

public class BEDAnnotationSource extends AbstractAnnotationSource<BEDAnnotation> {
    public class BEDAnnotation implements Annotation {
        final private String name;
        final private double score;
        final private GenomeSpan coord;
        
        public BEDAnnotation(GenomeSpan coord, String name, double score) {
            this.coord = coord;
            this.name = name;
            this.score = score;
        }

        @Override
        public String[] toStringArray() {
            return new String[] { name, Double.toString(score) };
        }

        @Override
        public String toString() {
            return name;
        }
        
        public GenomeSpan getCoord() {
            return coord;
        }
    }

    public BEDAnnotationSource(String filename) throws FileNotFoundException, IOException {
        loadFile(new FileInputStream(new File(filename)));
    }
    public BEDAnnotationSource(InputStream is) throws FileNotFoundException, IOException {
        loadFile(is);
    }

    protected void loadFile(InputStream is) {
        for (final String line : new StringLineReader(is)) {
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

            
            final GenomeSpan coord = new GenomeSpan(chrom, start, end, strand);
            final BEDAnnotation annotation = new BEDAnnotation(coord, name, score);

            addAnnotation(coord, annotation);
            
        }

    }
    
    @Override
    public String[] getAnnotationNames() {
        return new String[] { "name", "score" };
    }

}
