package io.compgen.ngsutils.fastq.filter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.compgen.ngsutils.fastq.FastqRead;

public class ExcludeListFilter extends AbstractSingleReadFilter {
	private Set<String> names = new HashSet<String>();
	public ExcludeListFilter(Iterable<FastqRead> parent, boolean verbose, String listFilename) throws FilteringException, IOException {
		super(parent, verbose);

		BufferedReader br = new BufferedReader(new FileReader(listFilename));
		for (String line; (line=br.readLine()) != null;) {
		    names.add(line);
		}
		br.close();
		
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] exclude list: " + listFilename + " (N=" + names.size() +")");
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
	    if (names.contains(read.getName())) {
	        if (verbose) {
	            System.err.println("Removed: " + read.getName());
	        }
	        return null;
	    }

	    return read;
	}
}
