package io.compgen.ngsutils.fastq.filter;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fastq.FastqRead;

public class NameSubstring extends AbstractSingleReadFilter {
	private final String[] substrs;
	
    public NameSubstring(Iterable<FastqRead> parent, boolean verbose, String substr) throws FilteringException {
        this(parent, verbose, new String[]{substr});
    }
    public NameSubstring(Iterable<FastqRead> parent, boolean verbose, String substr1, String substr2) throws FilteringException {
        this(parent, verbose, new String[]{substr1, substr2});
    }
    public NameSubstring(Iterable<FastqRead> parent, boolean verbose, String[] substrs) throws FilteringException {
		super(parent, verbose);
		this.substrs = substrs;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Substring: " + StringUtils.join(",", substrs));
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
	    String name = read.getName();
	    
	    if (read.getComment()!=null) {
	        name = name + " " + read.getComment();
	    }
	    
	    for (String substr: substrs) {
	        if (!name.contains(substr)) {
	            return null;
	        }
	    }
	    return read;
	}
}
