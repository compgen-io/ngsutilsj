package org.ngsutils.annotation;

import org.ngsutils.annotation.SimpleAnnotationSource.SimpleValueAnnotation;
import org.ngsutils.bam.Strand;

public class SimpleAnnotationSource extends AbstractAnnotationSource<SimpleValueAnnotation> {

    public class SimpleValueAnnotation implements Annotation, Comparable<SimpleValueAnnotation> {
        final private String value;
        final private GenomeSpan coord;
        public SimpleValueAnnotation(GenomeSpan coord, String value) {
            this.coord = coord;
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        
        public String toString() {
            return value;
        }
        
        @Override
        public String[] toStringArray() {
            return new String[]{value};
        }

        @Override
        public int compareTo(SimpleValueAnnotation o) {
            return value.compareTo(o.getValue());
        }

        @Override
        public GenomeSpan getCoord() {
            return coord;
        }

    }

    public void addAnnotation(GenomeSpan coord, String val) {
        this.addAnnotation(coord, new SimpleValueAnnotation(coord, val));
    }
    
    public void addAnnotation(String ref, int pos, String val) {
        addAnnotation(new GenomeSpan(ref, pos, Strand.NONE), val);
    }
    
    public void addAnnotation(String ref, int pos, Strand strand, String val) {
        addAnnotation(new GenomeSpan(ref, pos, strand), val);
    }
    
    public void addAnnotation(String ref, int start, int end, String val) {
        addAnnotation(new GenomeSpan(ref, start, end, Strand.NONE), val);
    }

    public void addAnnotation(String ref, int start, int end, Strand strand, String val) {
        addAnnotation(new GenomeSpan(ref, start, end, strand), val);
    }
    
    
    @Override
    public String[] getAnnotationNames() {
        return new String[]{"value"};
    }
}
