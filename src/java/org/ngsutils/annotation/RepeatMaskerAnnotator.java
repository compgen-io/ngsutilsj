package org.ngsutils.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;

/**
 * Loads annotations from a RepeatMasker output
 * @author mbreese
 *
 */
public class RepeatMaskerAnnotator implements Annotator {
    private Map<String, List<GenomeCoordinates>> repeats = new HashMap<String, List<GenomeCoordinates>>();

    public RepeatMaskerAnnotator(String filename) throws FileNotFoundException, IOException {
        int skip = 3;
        for (String line: new StringLineReader(filename)) {
            if (skip > 0) {
                skip--;
                continue;
            }
            
            String[] cols = StringUtils.strip(line).split(" +", -1);
            String chrom = cols[4];
            int start = Integer.parseInt(cols[5]);
            int end = Integer.parseInt(cols[6]);
            Strand strand;

            if (cols[8].equals("+")) {
                strand = Strand.PLUS;
            } else if (cols[8].equals("C")) {
                strand = Strand.MINUS;
            } else{
                strand = Strand.NONE;                
            }

            String[] annotations = new String[] { cols[9], cols[10] };
            
            if (!repeats.containsKey(chrom)) {
                repeats.put(chrom, new ArrayList<GenomeCoordinates>());
            }
            
            repeats.get(chrom).add(new GenomeCoordinates(chrom, start, end, strand, annotations));
        }

        for (String chrom: repeats.keySet()) {
            Collections.sort(repeats.get(chrom));
        }        
    }
    
    @Override
    public String[] getAnnotationNames() {
        return new String[] { "repeat", "repeat_family" };
    }

    @Override
    public List<String[]> findAnnotation(String ref, int start) {
        return findAnnotation(ref, start, start, Strand.NONE);
    }

    @Override
    public List<String[]> findAnnotation(String ref, int start, int end) {
        return findAnnotation(ref, start, end, Strand.NONE);
    }

    @Override
    public List<String[]> findAnnotation(String ref, int start, int end, Strand strand) {
        List<String[]> outs = new ArrayList<String[]> ();         
        if (!repeats.containsKey(ref)) {
            return outs;
        }
        
        /* TODO: change this to use a NavigableMap/Set */
        for (GenomeCoordinates coord: repeats.get(ref)) {
            if (coord.contains(start,  end, strand)) {
                outs.add(coord.annotations);
            }
            if (coord.start > start) {
                break;
            }
        }
        return outs;
    }
}
