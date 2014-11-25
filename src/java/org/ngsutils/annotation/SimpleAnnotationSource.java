package org.ngsutils.annotation;

import org.ngsutils.annotation.SimpleAnnotationSource.SimpleValueAnnotation;
import org.ngsutils.bam.Strand;

public class SimpleAnnotationSource extends AbstractAnnotationSource<SimpleValueAnnotation> {

    public class SimpleValueAnnotation implements Annotation, Comparable<SimpleValueAnnotation> {
        final private String value;
        final private GenomeRegion coord;
        public SimpleValueAnnotation(GenomeRegion coord, String value) {
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
        public GenomeRegion getCoord() {
            return coord;
        }

    }

    public void addAnnotation(GenomeRegion coord, String val) {
        this.addAnnotation(coord, new SimpleValueAnnotation(coord, val));
    }
    
    public void addAnnotation(String ref, int pos, String val) {
        addAnnotation(new GenomeRegion(ref, pos, Strand.NONE), val);
    }
    
    public void addAnnotation(String ref, int pos, Strand strand, String val) {
        addAnnotation(new GenomeRegion(ref, pos, strand), val);
    }
    
    public void addAnnotation(String ref, int start, int end, String val) {
        addAnnotation(new GenomeRegion(ref, start, end, Strand.NONE), val);
    }

    public void addAnnotation(String ref, int start, int end, Strand strand, String val) {
        addAnnotation(new GenomeRegion(ref, start, end, strand), val);
    }
    
    
    @Override
    public String[] getAnnotationNames() {
        return new String[]{"value"};
    }
}
