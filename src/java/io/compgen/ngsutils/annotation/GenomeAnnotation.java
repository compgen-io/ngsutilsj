package io.compgen.ngsutils.annotation;

public class GenomeAnnotation<T> implements Comparable<GenomeAnnotation<T>>{
    final private GenomeSpan coordinates;
    final private T value;

    public GenomeAnnotation(GenomeSpan coordinates, T value) {
        this.coordinates = coordinates;
        this.value = value;
    }

    public GenomeSpan getCoordinates() {
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
