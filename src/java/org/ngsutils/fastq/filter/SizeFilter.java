package org.ngsutils.fastq.filter;

import org.ngsutils.fastq.FastqRead;

public class SizeFilter extends AbstractSingleReadFilter {
	private int minLength;
	public SizeFilter(Iterable<FastqRead> parent, boolean verbose, int minLength) {
		super(parent, verbose);
		this.minLength = minLength;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing reads shorter than: " + minLength);
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
		if (read.getQual().length() >= this.minLength) {
			return read;
		}
		return null;
	}
}
