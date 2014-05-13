package org.ngsutils.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;

/**
 * Loads annotations from a RepeatMasker output
 * 
 * @author mbreese
 * 
 */
public class RepeatMaskerAnnotator implements Annotator {
    private final NavigableMap<GenomeCoordinates, List<String[]>> repeats = new TreeMap<GenomeCoordinates, List<String[]>>();

    public RepeatMaskerAnnotator(String filename) throws FileNotFoundException, IOException {
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
            final GenomeCoordinates coord = new GenomeCoordinates(chrom, start, end, strand);
            final String[] annotations = new String[] { cols[9], cols[10] };

            if (!repeats.containsKey(coord)) {
                repeats.put(coord, new ArrayList<String[]>());
            }

            repeats.get(coord).add(annotations);
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
        final List<String[]> outs = new ArrayList<String[]>();

        final GenomeCoordinates coord = new GenomeCoordinates(ref, start, end, strand);
        final GenomeCoordinates floor = repeats.floorKey(coord);
        final GenomeCoordinates ceil = repeats.ceilingKey(coord);
        final SortedMap<GenomeCoordinates, List<String[]>> submap = repeats.subMap(floor, true,
                ceil, true);
        for (final GenomeCoordinates key : submap.keySet()) {
            if (key.contains(coord)) {
                outs.addAll(submap.get(key));
            }
        }

        return outs;
    }
}
