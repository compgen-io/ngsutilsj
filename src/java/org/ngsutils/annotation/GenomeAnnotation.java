package org.ngsutils.annotation;

public class GenomeAnnotation<T> implements Comparable<GenomeAnnotation<T>>{
    final private GenomeRegion coordinates;
    final private T value;

    public GenomeAnnotation(GenomeRegion coordinates, T value) {
        this.coordinates = coordinates;
        this.value = value;
    }

    public GenomeRegion getCoordinates() {
        return coordinates;
    }

    public T getValue() {
        return value;
    }

    @Override
    public int compareTo(GenomeAnnotation<T> o) {
        return coordinates.compareTo(o.coordinates);
    }

    public String toString() {
        return coordinates + " => "+value;
    }


}
