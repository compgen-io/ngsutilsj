package io.compgen.ngsutils.support;

import java.util.Iterator;

public class BufferedIteratorImpl<T> implements BufferedIterator<T>{
	private final Iterator<T> it;
	
	protected T cur=null;
	protected boolean closed = false;
	
	public BufferedIteratorImpl(Iterator<T> it) {
		this.it = it;
	}
	
	public boolean hasNext() {
		if (this.cur == null && it.hasNext()) {
			this.cur = it.next();
		}
		return this.cur != null;
	}
	
	public T next() {
		if (this.cur == null && it.hasNext()) {
			this.cur = it.next();
		}

		T tmp = cur;
		cur = null;
		
		return tmp;
	}

	public T peek() {
		if (this.cur == null && it.hasNext()) {
			this.cur = it.next();
		}
		
		return this.cur;
	}
}
