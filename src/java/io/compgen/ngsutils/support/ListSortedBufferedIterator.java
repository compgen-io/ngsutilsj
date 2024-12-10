package io.compgen.ngsutils.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListSortedBufferedIterator<T extends Comparable<? super T>> implements BufferedIterator<T>{
	private List<T> buf = new ArrayList<T>();
	
	public void add(T val) {
		buf.add(val);
		Collections.sort(buf);
	}
	
	@Override
	public boolean hasNext() {
		return buf.size() > 0;
	}

	@Override
	public T next() {
		if (buf.size() > 0) {
			T ret = buf.remove(0);
			return ret;
		}
		return null;
	}

	@Override
	public T peek() {
		if (buf.size() > 0) {
			return buf.get(0);
		}
		return null;
	}
}
