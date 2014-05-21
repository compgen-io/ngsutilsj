package org.ngsutils.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngsutils.bam.Strand;

abstract public class AbstractAnnotator<T> implements Annotator<T> {
    public static class RefBin {
        final private String ref;
        final private int bin;

        public RefBin(String ref, int bin) {
            super();
            this.ref = ref;
            this.bin = bin;
        }
        @Override
        public String toString() {
            return ref+"/"+bin;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + bin;
            result = prime * result + ((ref == null) ? 0 : ref.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RefBin other = (RefBin) obj;
            if (bin != other.bin) {
                return false;
            }
            if (ref == null) {
                if (other.ref != null) {
                    return false;
                }
            } else if (!ref.equals(other.ref)) {
                return false;
            }
            return true;
        }
        public static List<RefBin> getBins(GenomeCoordinates coord) {
            int start = coord.start / BINSIZE;
            int end = coord.end / BINSIZE;

            List<RefBin> bins = new ArrayList<RefBin>();
            for (int i=start; i<=end; i++) {
                bins.add(new RefBin(coord.ref, i));
            }
             
            return bins;       
        }
    }
    
    protected final Map<RefBin,List<GenomeAnnotation<T>>> annotationBins = new HashMap<RefBin, List<GenomeAnnotation<T>>>();
    protected final static int BINSIZE=10_000;
    
    @Override
    public List<T> findAnnotation(String ref, int start) {
        return findAnnotation(new GenomeCoordinates(ref, start, Strand.NONE));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, int end) {
        return findAnnotation(new GenomeCoordinates(ref, start, end, Strand.NONE));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, Strand strand) {
        return findAnnotation(new GenomeCoordinates(ref, start, strand));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, int end, Strand strand) {
        return findAnnotation(new GenomeCoordinates(ref, start, end, strand));
    }

    @Override
    public List<T> findAnnotation(final GenomeCoordinates coord) {
        final List<T> outs = new ArrayList<T>();
//        System.err.println("Searching for: "+ coord);

        
        for (RefBin bin: RefBin.getBins(coord)) {
            if (annotationBins.containsKey(bin)) {
                for (GenomeAnnotation<T> ga: annotationBins.get(bin)) {
                    if (ga.getCoordinates().start > coord.end) {
//                        System.err.println("      *** break ***");
                        break;
                    }
//                    System.err.print("    - checking ga: "+ga.getCoordinates());
                    if (ga.getCoordinates().contains(coord, false)) {
//                        System.err.print(" **MATCH**");
                        outs.add(ga.getValue());
                    }
//                    System.err.println("");
                }                
            }
        }
        
        return outs;
    }
    
    protected void addAnnotation(GenomeCoordinates coord, T value) {
        for (RefBin bin: RefBin.getBins(coord)) {
//            System.err.println("Adding: " + coord + " to bin: " + bin);
            if (!annotationBins.containsKey(bin)) {
                annotationBins.put(bin,  new ArrayList<GenomeAnnotation<T>>());
            }
            annotationBins.get(bin).add(new GenomeAnnotation<T>(coord, value));
            Collections.sort(annotationBins.get(bin));
        }
    }

    @Override
    public boolean provides(String key) {
        final String[] names = getAnnotationNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

}
