package io.compgen.ngsutils.support;

import java.util.Iterator;

public class PeakableIterator<T> implements Iterator<T> {
	final private Iterator<T> parent;
	private T next = null;

	public PeakableIterator(Iterator<T> it) {
		this.parent = it;
	}
	
	public T peek() {
		populate();
		return next;
	}
	
	@Override
	public boolean hasNext() {
		populate();
		return next != null;
		
	}
	@Override
	public T next() {
		T ret = next;
		next = null;
		
		return ret;
	}

	private void populate() {
		if (next == null && parent.hasNext()) {
			next = parent.next();
		}
	}
}
