package org.ngsutils.cli.fastq.filter;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.fastq.FastqRead;

public class WildcardFilter extends AbstractSingleReadFilter {
	private int maxWildcards;
	public WildcardFilter(Iterable<FastqRead> parent, boolean verbose, int maxWildcards) throws NGSUtilsException {
		super(parent, verbose);
        if (maxWildcards < 0) {
            throw new NGSUtilsException("Number of wildcard calls must be zero or greater!");
        }
		this.maxWildcards = maxWildcards;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing reads with more than: " + maxWildcards + " wildcard calls");
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
	    char[] wildcards = new char[]{'N', 'n', '.', '4','5','6'}; // supporting colorspace
	    
	    int matches = 0;
	    for (char c:read.getSeq().toCharArray()) {
	         for (char d:wildcards) {
	             if (c == d) {
	                 matches++;
	                 break;
	             }
	         }
	    }
	    
	    if (matches <= maxWildcards) {
	        return read;
	    }
	    
	    return null;
	}
}
