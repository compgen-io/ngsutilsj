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
		Iterator<T> it1 = foo.iterator();
		Iterator<U> it2 = bar.iterator();
		
		while (it1.hasNext() && it2.hasNext()) {
			T one = it1.next();
			U two = it2.next();
			handler.each(one,  two);
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
                break;
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
                    break;
                }
            }

        }
    }
}
