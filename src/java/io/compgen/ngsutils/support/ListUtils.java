package io.compgen.ngsutils.support;

import java.util.Iterator;
import java.util.List;

import io.compgen.common.Pair;

public class ListUtils<T> {
	private List<T> parent;
	public ListUtils(List<T> list) {
		this.parent = list;
	}
	
	public Iterable<Pair<Integer,T>> enumerate() {
		return new Iterable<Pair<Integer, T>>() {
			@Override
			public Iterator<Pair<Integer, T>> iterator() {
				return new Iterator<Pair<Integer, T>>() {
					int pos = 0;
					@Override
					public boolean hasNext() {
						return pos < parent.size();
					}

					@Override
					public Pair<Integer, T> next() {
						Pair<Integer, T> ret = new Pair<Integer, T>(pos, parent.get(pos));
						pos++;
						return ret;
					}};
			}	
		};		
	}
}
