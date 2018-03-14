package io.compgen.ngsutils.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
        public static List<RefBin> getBins(GenomeSpan coord) {
            
            if (coord.start == -1) {
                // whole chromosome - send it all
                
            }
            
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
    protected final SortedSet<GenomeAnnotation<T>> annotations = new TreeSet<GenomeAnnotation<T>>();
    protected final static int BINSIZE=100_000;
    
//    @Override
//    public List<T> findAnnotation(String ref, int start) {
//        return findAnnotation(new GenomeRegion(ref, start, Strand.NONE));
//    }
//
//    @Override
//    public List<T> findAnnotation(String ref, int start, int end) {
//        return findAnnotation(new GenomeRegion(ref, start, end, Strand.NONE));
//    }
//
//    @Override
//    public List<T> findAnnotation(String ref, int start, Strand strand) {
//        return findAnnotation(new GenomeRegion(ref, start, strand));
//    }
//
//    @Override
//    public List<T> findAnnotation(String ref, int start, int end, Strand strand) {
//        return findAnnotation(new GenomeRegion(ref, start, end, strand));
//    }

    @Override
    public List<T> findAnnotation(final GenomeSpan coord) {
        return findAnnotation(coord, false);
    }
    public List<T> findAnnotation(final GenomeSpan coord, boolean onlyWithin) {
        final Set<T> outs = new HashSet<T>();
       
        List<RefBin> refbins = new ArrayList<RefBin>();
        
        if (coord.start == -1) {
            // whole chrom
            for (RefBin bin: annotationBins.keySet()) {
                if (bin.ref.equals(coord.ref)) {
                    refbins.add(bin);
                }
            }
        } else {
            refbins = RefBin.getBins(coord);
        }
        
        for (RefBin bin: refbins) {            
            if (annotationBins.containsKey(bin)) {
                for (GenomeAnnotation<T> ga: annotationBins.get(bin)) {
                    if (coord.start != -1 && ga.getCoordinates().start > coord.end) {
//                        System.err.println("BREAK");
                        break;
                    }
//                    System.err.println("ga.getCoordinates() => " + ga.getCoordinates());
//                    System.err.println("coord => " + coord);
//                    System.err.println("ga.getCoordinates().overlap(coord) => " + ga.getCoordinates().overlaps(coord));
                    if (onlyWithin) {
                        if (ga.getCoordinates().contains(coord)) {
                            outs.add(ga.getValue());
                        }
                    } else if (ga.getCoordinates().overlaps(coord)) {
//                        System.err.println("OVERLAP");
                        outs.add(ga.getValue());
                    } else {
//                        System.err.println("NO OVERLAP :(");
                    }
                }                
            }
        }
        
        return new ArrayList<T>(outs);
    }
    
//    @Override
//    public boolean hasAnnotation(String ref, int start) {
//        return hasAnnotation(new GenomeRegion(ref, start, Strand.NONE));
//    }
//
//    @Override
//    public boolean hasAnnotation(String ref, int start, int end) {
//        return hasAnnotation(new GenomeRegion(ref, start, end, Strand.NONE));
//    }
//
//    @Override
//    public boolean hasAnnotation(String ref, int start, Strand strand) {
//        return hasAnnotation(new GenomeRegion(ref, start, strand));
//    }
//
//    @Override
//    public boolean hasAnnotation(String ref, int start, int end, Strand strand) {
//        return hasAnnotation(new GenomeRegion(ref, start, end, strand));
//    }

    @Override
    public boolean hasAnnotation(final GenomeSpan coord) {
        return hasAnnotation(coord, false);
    }
    @Override
    public boolean hasAnnotation(final GenomeSpan coord, boolean onlyWithin) {
        for (RefBin bin: RefBin.getBins(coord)) {
            if (annotationBins.containsKey(bin)) {
                for (GenomeAnnotation<T> ga: annotationBins.get(bin)) {
                    if (ga.getCoordinates().start > coord.end) {
                        break;
                    }
                    if (onlyWithin) {
                        if (ga.getCoordinates().contains(coord)) {
                            return true;
                        }
                    } else if (ga.getCoordinates().overlaps(coord)) {
                        return true;
                    }
                }                
            }
        }
        return false;
    }
    
    
    protected void addAnnotation(GenomeSpan coord, T value) {
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

    public int size() {
        return annotations.size();
    }
    
    public Iterator<GenomeAnnotation<T>> iterator() {
        return annotations.iterator();
    }
    public Iterator<GenomeSpan> regionsIterator() {
        return new Iterator<GenomeSpan> () {
            Iterator<GenomeAnnotation<T>> it = iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public GenomeSpan next() {
                return it.next().getCoordinates();
            }

            @Override
            public void remove() {
                it.remove();
            }};
    }
}
