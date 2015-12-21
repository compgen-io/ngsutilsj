package io.compgen.ngsutils.fastq.filter;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fastq.FastqRead;

public class SeqTrimFilter extends AbstractSingleReadFilter {
    final private int minOverlap;
    final private int readNum; // -1 = all reads, 1 = first read, 2 = second read
	final private String trimSeq;
	private String lastName=null;
	final private int[] threshold;

    public SeqTrimFilter(Iterable<FastqRead> parent, boolean verbose, String trimSeq, int minOverlap, double minPct, int readNum) {
        super(parent, verbose);
        this.trimSeq = trimSeq.toUpperCase();
        this.minOverlap = minOverlap;
        this.readNum = readNum;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing: " + this.trimSeq + " from 3' end of reads with an overlap of at least:" + minOverlap+ " with a minimum match pct: "+ minPct);
        }
        
        this.threshold = new int[trimSeq.length()+1];
        for (int i=minOverlap; i<=trimSeq.length(); i++) {
            threshold[i] = (int) Math.ceil(i * minPct);
        }
    }

    public SeqTrimFilter(Iterable<FastqRead> parent, boolean verbose, String trimSeq, int minOverlap, double minPct) {
        this(parent, verbose, trimSeq, minOverlap, minPct, -1);
    }

	@Override
	protected FastqRead filterRead(FastqRead read) {
	    int readNum = 1;
	    if (lastName != null && read.getName().equals(lastName)) {
	        readNum = 2;
	    }
	    lastName = read.getName();
	    
	    if (this.readNum > 0 && this.readNum != readNum) {
	        return read;
	    }
	    
	    int i = minOverlap;
	    while (i < read.getSeq().length()) {
	        int thres = threshold[Math.min(i, trimSeq.length())];
	        String subseq = StringUtils.sliceRight(read.getSeq(), -i);
	        if (verbose) {
	            System.err.print("["+this.getClass().getSimpleName()+"] checking: " + subseq);
	        }
	        int matches = StringUtils.matchCount(trimSeq, subseq);
            if (verbose) {
                System.err.println(" matches:" + matches+" min:" + thres);
            }
	        
	        if (matches >= thres) {
	            if (verbose) {
	                System.err.println("    remaining seq:" + (read.getSeq().length()-i));
	            }
	            if ((read.getSeq().length()-i) >0) {
                    String newseq = read.getSeq().substring(0, read.getSeq().length() - i);
                    String newqual = read.getQual().substring(0, read.getQual().length() - i);
                    String comment = read.getComment();
                    if (comment == null) {
                        if (this.readNum > 0) {
                            comment = "#trimseq"+this.readNum;
                        } else {
                            comment = "#trimseq";
                        }
                    } else {
                        if (this.readNum > 0) {
                            comment += " #trimseq"+this.readNum;
                        } else {
                            comment += " #trimseq";
                        }
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
