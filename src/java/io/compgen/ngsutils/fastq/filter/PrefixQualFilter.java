package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class PrefixQualFilter extends AbstractSingleReadFilter {
	private int minqual;
	public PrefixQualFilter(Iterable<FastqRead> parent, boolean verbose, int minqual) {
		super(parent, verbose);
		this.minqual = minqual;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing prefix-calls with a quality PHRED score less than: " + minqual);
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		int removed = 0;
		while (qual.length() > 0 && (qual.charAt(0) - 33) < minqual) {
			qual = qual.substring(1);
			removed++;
		}
		
		seq = seq.substring(removed);

		if (qual.length() > 0) {
		    if (removed > 0) {
    			if (comment == null) {
    				comment = "#prequal";
    			} else {
    				comment = comment + " #prequal";
    			}
		    }
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}
}
