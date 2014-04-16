package org.ngsutils.cli.fastq.filter;

import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.StringUtils;

public class SeqTrimFilter extends AbstractSingleReadFilter {
    private int minOverlap;
    private double minPct;
	private String trimSeq;

	public SeqTrimFilter(Iterable<FastqRead> parent, boolean verbose, String trimSeq, int minOverlap, double minPct) {
		super(parent, verbose);
		this.trimSeq = trimSeq.toUpperCase();
		this.minOverlap = minOverlap;
		this.minPct = minPct;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing: " + this.trimSeq + " from 3' end of reads with an overlap of at least:" + minOverlap+ " with a minimum match pct: "+ minPct);
        }
	}

	@Override
	protected FastqRead filterRead(FastqRead read) {
	    int i = minOverlap;
	    while (i < read.getSeq().length()) {
	        String subseq = StringUtils.sliceRight(read.getSeq(), -i);
	        if (verbose) {
	            System.err.print("["+this.getClass().getSimpleName()+"] checking: " + subseq);
	        }
	        int matches = StringUtils.matchCount(trimSeq, subseq);
            if (verbose) {
                System.err.println(" matches:" + matches+" pct:" + ((double)matches/i));
            }
	        
	        if (((double)matches/i) > minPct) {
	            if (verbose) {
	                System.err.println("    remaining seq:" + (read.getSeq().length()-i));
	            }
	            if ((read.getSeq().length()-i) >0) {
                    String newseq = read.getSeq().substring(0, read.getSeq().length() - i);
                    String newqual = read.getQual().substring(0, read.getQual().length() - i);
                    String comment = read.getComment();
                    if (comment == null) {
                        comment = "#trimseq";
                    } else {
                        comment += " #trimseq";
                    }
	                return new FastqRead(read.getName(), newseq, newqual, comment);
	            } else{
	                return null;
	            }
	        } else {
	            i++;
	        }
	    }
	    return read;
	}
}
