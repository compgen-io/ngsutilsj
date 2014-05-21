package org.ngsutils.annotation;

public class GenomeAnnotation<T> implements Comparable<GenomeAnnotation<T>>{
    final private GenomeCoordinates coordinates;
    final private T value;

    public GenomeAnnotation(GenomeCoordinates coordinates, T value) {
        this.coordinates = coordinates;
        this.value = value;
    }

    public GenomeCoordinates getCoordinates() {
        return coordinates;
    }

    public T getValue() {
        return value;
    }

    @Override
    public int compareTo(GenomeAnnotation<T> o) {
        return coordinates.compareTo(o.coordinates);
    }
    
}
