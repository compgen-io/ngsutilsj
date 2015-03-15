package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class SuffixQualFilter extends AbstractSingleReadFilter {
	private char qualval;
	public SuffixQualFilter(Iterable<FastqRead> parent, boolean verbose, char qualval) {
		super(parent, verbose);
		this.qualval = qualval;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing calls with a quality PHRED score of: " + qualval);
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		int removed = 0;
		while (qual.length() > 0 && qual.charAt(qual.length()-1) == qualval) {
			qual = qual.substring(0, qual.length() - 1);
			removed++;
		}
		
		seq = seq.substring(0, seq.length() - removed);

		if (qual.length() > 0) {
		    if (removed > 0) {
    			if (comment == null) {
    				comment = "#suffqual";
    			} else {
    				comment = comment + " #suffqual";
    			}
		    }
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}
}
