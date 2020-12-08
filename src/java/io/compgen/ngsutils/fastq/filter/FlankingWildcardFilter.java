package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class FlankingWildcardFilter extends AbstractSingleReadFilter {
	public FlankingWildcardFilter(Iterable<FastqRead> parent, boolean verbose) throws FilteringException {
		super(parent, verbose);
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing N bases from 5' and 3' end of reads");
        }
		
	}

	@Override
	protected FastqRead filterRead(FastqRead read) throws FilteringException {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		if (seq.length() != qual.length()) {
			throw new FilteringException("You cannot use the FlankingWildcardFilter with color-space files!");
		}

		boolean isChanged = false;
		
		while (seq.charAt(0) == 'N' || seq.charAt(0) == 'n' || seq.charAt(0) == '.') {
			isChanged = true;
			seq = seq.substring(1);
			qual = qual.substring(1);
		}

		while (seq.charAt(seq.length()-1) == 'N' || seq.charAt(seq.length()-1) == 'n' || seq.charAt(seq.length()-1) == '.') {
			isChanged = true;
			seq = seq.substring(0,seq.length()-1);
			qual = qual.substring(0,seq.length()-1);
		}

		if (isChanged) {
			if (comment == null) {
				comment = "#flanking_wildcard";
			} else {
				comment = comment + " #flanking_wildcard";
			}
			return new FastqRead(name, seq, qual, comment);
		}
		
		return read;
	}

}
