package org.ngsutils.annotation;

import java.util.List;
import java.util.Set;

import org.ngsutils.bam.Strand;

public interface AnnotationSource<T> {
    /** 
     * headers / annotation keys
     * @return
     */
    public String[] getAnnotationNames();
    public Set<GenomeAnnotation<T>> allAnnotations();

    /**
     * Find all of of the Annotation values for these coordinates. There may be more than one for each coordinate.
     * 
     * @param ref - chromosome
     * @param start - zero-based coordinate!
     * @param end - end of the span (can be equal to start)
     * @param strand - if known
     * @return
     */
    public List<T> findAnnotation(GenomeRegion coord);
    public List<T> findAnnotation(String ref, int start);
    public List<T> findAnnotation(String ref, int start, Strand strand);
    public List<T> findAnnotation(String ref, int start, int end);
    public List<T> findAnnotation(String ref, int start, int end, Strand strand);

    /**
     * Does the annotator provide values of the name key
     * @param string
     * @return
     */
    public boolean provides(String key);
}
