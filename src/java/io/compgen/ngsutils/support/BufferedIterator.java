package io.compgen.ngsutils.support;

public interface BufferedIterator<T> {
	public boolean hasNext();
	public T peek();
	public T next();
}
