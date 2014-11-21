package org.ngsutils.support;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class IterUtils {
	public interface Each<T, U> {
		public void each(T foo, U bar);
	}
    public interface EachList<T> {
        public void each(List<T> foo);
    }

    public static <T,U> void zip(Iterable<T> foo, Iterable<U> bar, Each<T,U> handler) {
        zip(foo, bar, handler, false);
    }
    public static <T,U> void zip(Iterable<T> foo, Iterable<U> bar, Each<T,U> handler, boolean flush) {
		Iterator<T> it1 = foo.iterator();
		Iterator<U> it2 = bar.iterator();
		
		while (it1.hasNext() && it2.hasNext()) {
            T one = it1.next();
            U two = it2.next();
            handler.each(one,  two);
		}
		
		if (flush) {
            while (it1.hasNext()) {
                T one = it1.next();
                U two = null;
                handler.each(one,  two);
            }
            while (it2.hasNext()) {
                T one = null;
                U two = it2.next();
                handler.each(one,  two);
            }
		} else {
		    // clear out the longer of the iterators...
            while (it1.hasNext()) {
                it1.next();
            }
            while (it2.hasNext()) {
                it2.next();
            }
		}
	}

    public static <T> void zipArray(Iterable<T>[] foo, EachList<T> handler) {
        @SuppressWarnings("unchecked")
        Iterator<T>[] its = new Iterator[foo.length];
        
        for (int i=0; i<foo.length; i++) {
            its[i] = foo[i].iterator();
        }

        boolean hasNext = true;
        for (int i=0; i<foo.length; i++) {
            if (!its[i].hasNext()) {
                hasNext = false;
            }
        }
        
        while (hasNext) {
            List<T> out = new ArrayList<T>();
            
            for (int i=0; i<foo.length; i++) {
                out.add(its[i].next());
            }

            handler.each(out);
            
            hasNext = true;
            for (int i=0; i<foo.length; i++) {
                if (!its[i].hasNext()) {
                    hasNext = false;
                }
            }
        }

        // clear out iterators
        hasNext = true;
        while (hasNext) {
            hasNext = false;
            for (int i=0; i<foo.length; i++) {
                if (its[i].hasNext()) {
                    its[i].next();
                    hasNext = true;
                }
            }
        }
    }
    
    public static <T> Iterable<T> wrapIterator(final Iterator<T> it) {
        return new Iterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return it;
            }};
        
    }
}
