package io.compgen.ngsutils.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.RepeatMaskerAnnotationSource.RepeatAnnotation;
import io.compgen.ngsutils.bam.Strand;

/**
 * Loads annotations from a RepeatMasker output
 * 
 * @author mbreese
 * 
 */

public class RepeatMaskerAnnotationSource extends AbstractAnnotationSource<RepeatAnnotation> {
    public class RepeatAnnotation implements Annotation {
        final private String repeat;
        final private String repeatFamily;
        final private GenomeSpan coord;
        public RepeatAnnotation(GenomeSpan coord, String repeat, String repeatFamily) {
            this.coord = coord;
            this.repeat = repeat;
            this.repeatFamily = repeatFamily;
        }

        public String getRepeat() {
            return repeat;
        }

        public String getRepeatFamily() {
            return repeatFamily;
        }

        @Override
        public String[] toStringArray() {
            return new String[] { repeat, repeatFamily };
        }

        @Override
        public GenomeSpan getCoord() {
            return coord;
        }
    }

    public RepeatMaskerAnnotationSource(String filename) throws FileNotFoundException, IOException {
        int skip = 3;
        for (final String line : new StringLineReader(filename)) {
            if (skip > 0) {
                skip--;
                continue;
            }

            final String[] cols = StringUtils.strip(line).split(" +", -1);
            final String chrom = cols[4];
            final int start = Integer.parseInt(cols[5]) - 1; // file is 1-based
            final int end = Integer.parseInt(cols[6]);
            Strand strand;

            if (cols[8].equals("+")) {
                strand = Strand.PLUS;
            } else if (cols[8].equals("C")) {
                strand = Strand.MINUS;
            } else {
                // this shouldn't happen
                strand = Strand.NONE;
            }
            final GenomeSpan coord = new GenomeSpan(chrom, start, end, strand);
            final RepeatAnnotation annotation = new RepeatAnnotation(coord, cols[9], cols[10]);

            addAnnotation(coord, annotation);
            
        }
    }

    @Override
    public String[] getAnnotationNames() {
        return new String[] { "repeat", "repeat_family" };
    }

}
