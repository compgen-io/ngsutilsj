package org.ngsutils.annotation;

import java.util.List;

import org.ngsutils.bam.Strand;

public interface Annotator {
    /** 
     * headers / annotation keys
     * @return
     */
    public String[] getAnnotationNames();
    
    /**
     * Find all of of the Annotation values for these coordinates. There may be more than one for each coordinate.
     * The order of each String[] is the same order as `getAnnotationNames`; 
     * 
     * @param ref - chromosome
     * @param start - zero-based coordinate!
     * @param end - end of the span (can be equal to start)
     * @param strand - if known
     * @return
     */
    public List<String[]> findAnnotation(String ref, int start, int end, Strand strand);
    public List<String[]> findAnnotation(String ref, int start);
    public List<String[]> findAnnotation(String ref, int start, int end);
}
