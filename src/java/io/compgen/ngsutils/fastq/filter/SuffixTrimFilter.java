package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class SuffixTrimFilter extends AbstractSingleReadFilter {
	private int removeSize;
	public SuffixTrimFilter(Iterable<FastqRead> parent, boolean verbose, int removeSize) throws FilteringException {
		super(parent, verbose);
		if (removeSize < 0) {
			throw new FilteringException("Number of bases to remove must be greated than zero!");
		}
		this.removeSize = removeSize;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing: " + removeSize + "bases from 3' end of reads");
        }
		
	}

	@Override
	protected FastqRead filterRead(FastqRead read) throws FilteringException {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		seq = seq.substring(0,seq.length()-removeSize);
		qual = qual.substring(0,seq.length()-removeSize);
		if (qual.length() > 0) {
			if (comment == null) {
				comment = "#suffix";
			} else {
				comment = comment + " #suffix";
			}
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}

}
