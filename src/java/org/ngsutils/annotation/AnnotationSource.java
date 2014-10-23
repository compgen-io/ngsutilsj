package org.ngsutils.annotation;

import java.util.List;
import java.util.Set;

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
    public List<T> findAnnotation(GenomeRegion coord, boolean onlyWithin);
//    public List<T> findAnnotation(String ref, int start);
//    public List<T> findAnnotation(String ref, int start, Strand strand);
//    public List<T> findAnnotation(String ref, int start, int end);
//    public List<T> findAnnotation(String ref, int start, int end, Strand strand);

    /**
     * Find all of of the Annotation values for these coordinates. There may be more than one for each coordinate.
     * 
     * @param ref - chromosome
     * @param start - zero-based coordinate!
     * @param end - end of the span (can be equal to start)
     * @param strand - if known
     * @return
     */
    public boolean hasAnnotation(GenomeRegion coord);
    public boolean hasAnnotation(GenomeRegion coord, boolean onlyWithin);
//    public boolean hasAnnotation(String ref, int start);
//    public boolean hasAnnotation(String ref, int start, Strand strand);
//    public boolean hasAnnotation(String ref, int start, int end);
//    public boolean hasAnnotation(String ref, int start, int end, Strand strand);

    /**
     * Does the annotator provide values of the name key
     * @param string
     * @return
     */
    public boolean provides(String key);
    
    /**
     * The total number of annotations in this source (-1) if not available
     * @return
     */
    public int size();
}
