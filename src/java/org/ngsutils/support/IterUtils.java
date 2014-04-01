package org.ngsutils.support;

import java.util.Iterator;


public class IterUtils {
	public interface Each<T, U> {
		public void each(T foo, U bar);
	}

	public static <T,U> void zip(Iterable<T> foo, Iterable<U> bar, Each<T,U> handler) {
		Iterator<T> it1 = foo.iterator();
		Iterator<U> it2 = bar.iterator();
		
		while (it1.hasNext() && it2.hasNext()) {
			T one = it1.next();
			U two = it2.next();
			handler.each(one,  two);
		}
	}
}
