package io.compgen.ngsutils.support.progress;

public interface ProgressMessage<T> {
    public String msg(T current);
}
