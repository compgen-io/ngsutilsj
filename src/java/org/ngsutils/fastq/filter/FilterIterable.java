package org.ngsutils.fastq.filter;

import java.util.Iterator;

import org.ngsutils.fastq.FastqRead;

public class FilterIterable implements Iterable<FastqRead>{
	private Filter filter;
	public FilterIterable(Filter filter) {
		this.filter = filter;
	}
	@Override
	public Iterator<FastqRead> iterator() {
		return filter;
	}

	public int getTotal() {
		return filter.getTotal();
	}
	public int getAltered() {
		return filter.getAltered();
	}
	public int getRemoved() {
		return filter.getRemoved();
	}
	public String getName() {
		return filter.getClass().getSimpleName();
	}	
}
