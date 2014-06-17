package org.ngsutils.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ngsutils.bam.Strand;

abstract public class AbstractAnnotationSource<T> implements AnnotationSource<T> {
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
        public static List<RefBin> getBins(GenomeRegion coord) {
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
    protected final Set<GenomeAnnotation<T>> annotations = new HashSet<GenomeAnnotation<T>>();
    protected final static int BINSIZE=100_000;
    
    @Override
    public List<T> findAnnotation(String ref, int start) {
        return findAnnotation(new GenomeRegion(ref, start, Strand.NONE));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, int end) {
        return findAnnotation(new GenomeRegion(ref, start, end, Strand.NONE));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, Strand strand) {
        return findAnnotation(new GenomeRegion(ref, start, strand));
    }

    @Override
    public List<T> findAnnotation(String ref, int start, int end, Strand strand) {
        return findAnnotation(new GenomeRegion(ref, start, end, strand));
    }

    @Override
    public List<T> findAnnotation(final GenomeRegion coord) {
        final Set<T> outs = new HashSet<T>();
       
        for (RefBin bin: RefBin.getBins(coord)) {
            if (annotationBins.containsKey(bin)) {
                for (GenomeAnnotation<T> ga: annotationBins.get(bin)) {
                    if (ga.getCoordinates().start > coord.end) {
                        break;
                    }
                    if (ga.getCoordinates().contains(coord, false)) {
                        outs.add(ga.getValue());
                    }
                }                
            }
        }
        
        return new ArrayList<T>(outs);
    }
    
    protected void addAnnotation(GenomeRegion coord, T value) {
        GenomeAnnotation<T> ga = new GenomeAnnotation<T>(coord, value);
        for (RefBin bin: RefBin.getBins(coord)) {
            if (!annotationBins.containsKey(bin)) {
                annotationBins.put(bin, new ArrayList<GenomeAnnotation<T>>());
            }
            annotationBins.get(bin).add(ga);
            Collections.sort(annotationBins.get(bin));
        }
        annotations.add(ga);
    }
    
    public Set<GenomeAnnotation<T>> allAnnotations() {
        return annotations;
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
