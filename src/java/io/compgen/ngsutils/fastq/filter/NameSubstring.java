package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class NameSubstring extends AbstractSingleReadFilter {
	private final String substr;
	public NameSubstring(Iterable<FastqRead> parent, boolean verbose, String substr) throws FilteringException {
		super(parent, verbose);
		this.substr = substr;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Substring: " + substr);
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
	    return read.getName().contains(substr) ? read: null;
	}
}
