package io.compgen.ngsutils.annotation;

import java.util.Iterator;
import java.util.List;

public interface AnnotationSource<T> {
    /** 
     * headers / annotation keys
     * @return
     */
    public String[] getAnnotationNames();

    /**
     * Find all of of the Annotation values for these coordinates. There may be more than one for each coordinate.
     * 
     * @param ref - chromosome
     * @param start - zero-based coordinate!
     * @param end - end of the span (can be equal to start)
     * @param strand - if known
     * @return
     */
    public List<T> findAnnotation(GenomeSpan coord);
    public List<T> findAnnotation(GenomeSpan coord, boolean onlyWithin);

    public boolean hasAnnotation(GenomeSpan coord);
    public boolean hasAnnotation(GenomeSpan coord, boolean onlyWithin);

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
    
    public Iterator<GenomeAnnotation<T>> iterator();
}
