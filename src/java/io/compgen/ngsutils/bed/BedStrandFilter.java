package io.compgen.ngsutils.bed;

import java.util.Iterator;

import io.compgen.ngsutils.bam.Strand;

public class BedStrandFilter implements Iterator<BedRecord>{
	protected final Iterator<BedRecord> it;
	protected final Strand strand;
	
	protected BedRecord cur = null;
	
    public BedStrandFilter(Iterator<BedRecord> it, Strand strand) {
    	this.it = it;
    	this.strand = strand;
    }
	@Override
	public boolean hasNext() {
		if (cur == null && it.hasNext()) {
			populate();
		}
		return cur != null;
	}
	@Override
	public BedRecord next() {
		BedRecord ret = cur;
		cur = null;
		populate();
		
		return ret;
	}
	
	private void populate() {
		while (it.hasNext()) {
			BedRecord tmp = it.next();
			if (strand.matches(tmp.coord.strand)) {
				cur = tmp;
				return;
			}
		}
	}
}

