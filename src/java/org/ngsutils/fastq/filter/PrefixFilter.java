package org.ngsutils.fastq.filter;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.fastq.FastqRead;

public class PrefixFilter extends AbstractSingleReadFilter {
	private int removeSize;
	public PrefixFilter(Iterable<FastqRead> parent, boolean verbose, int removeSize) throws NGSUtilsException {
		super(parent, verbose);
		if (removeSize < 0) {
			throw new NGSUtilsException("Number of bases to remove must be greated than zero!");
		}
		this.removeSize = removeSize;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing: " + removeSize + "bases from 5' end of reads");
        }
		
	}

	@Override
	protected FastqRead filterRead(FastqRead read) throws NGSUtilsException {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		if (seq.length() != qual.length()) {
			throw new NGSUtilsException("You cannot use the PrefixFilter with color-space files!");
		}
		
		seq = seq.substring(removeSize);
		qual = qual.substring(removeSize);
		if (qual.length() > 0) {
			if (comment == null) {
				comment = "#prefix";
			} else {
				comment = comment + " #prefix";
			}
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}

}
