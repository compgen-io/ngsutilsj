package org.ngsutils.fastq.filter;

import org.ngsutils.fastq.FastqRead;

public class PrefixQualFilter extends AbstractSingleReadFilter {
	private char qualval;
	public PrefixQualFilter(Iterable<FastqRead> parent, boolean verbose, char qualval) {
		super(parent, verbose);
		this.qualval = qualval;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing prefix-calls with a quality PHRED score of: " + qualval);
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		int removed = 0;
		while (qual.length() > 0 && qual.charAt(0) == qualval) {
			qual = qual.substring(1);
			removed++;
		}
		
		seq = seq.substring(seq.length() - removed);

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
