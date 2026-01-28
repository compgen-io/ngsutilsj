package io.compgen.ngsutils.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class CachedIterator<T> implements Iterator<T> {
	private final Iterator<T> parent;
	private final List<T> cache = new ArrayList<T>();
	private int index = 0;
	private boolean parentExhausted = false;
	
	public CachedIterator(Iterator<T> parent) {
		this.parent = parent;
	}
	
	@Override
	public boolean hasNext() {
		if (index < cache.size()) {
			return true;
		}
		if (parentExhausted) {
			return false;
		}
		if (parent.hasNext()) {
			return true;
		}
		parentExhausted = true;
		return false;
	}
	
	@Override
	public T next() {
		if (index < cache.size()) {
			return cache.get(index++);
		}
		if (parentExhausted || !parent.hasNext()) {
			parentExhausted = true;
			throw new NoSuchElementException();
		}
		T next = parent.next();
		cache.add(next);
		index++;
		return next;
	}
	
	public void reset() {
		index = 0;
	}
	
	public boolean isExhausted() {
		return parentExhausted;
	}
	
	public List<T> getCache() {
		return Collections.unmodifiableList(cache);
	}
}
