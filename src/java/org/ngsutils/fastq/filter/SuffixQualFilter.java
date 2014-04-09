package org.ngsutils.fastq.filter;

import org.ngsutils.fastq.FastqRead;

public class SuffixQualFilter extends SingleReadFilter {
	private char qualval;
	public SuffixQualFilter(Iterable<FastqRead> parent, boolean verbose, char qualval) {
		super(parent, verbose);
		this.qualval = qualval;
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		int removed = 0;
		while (qual.charAt(qual.length()-1) == qualval) {
			qual = qual.substring(0, qual.length() - 1);
			removed++;
		}
		
		seq = seq.substring(0, seq.length() - removed);

		if (qual.length() > 0) {
			if (comment == null) {
				comment = "#suffqual";
			} else {
				comment = comment + " #suffqual";
			}
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}
}
