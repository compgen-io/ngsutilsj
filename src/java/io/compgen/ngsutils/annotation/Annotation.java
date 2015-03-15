package io.compgen.ngsutils.annotation;

public interface Annotation {
    public String[] toStringArray();
    public GenomeSpan getCoord();
}
