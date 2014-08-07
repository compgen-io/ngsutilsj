package org.ngsutils.fastq.filter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.fastq.FastqRead;

public class WhitelistFilter extends AbstractSingleReadFilter {
	private Set<String> names = new HashSet<String>();
	public WhitelistFilter(Iterable<FastqRead> parent, boolean verbose, String listFilename) throws NGSUtilsException, IOException {
		super(parent, verbose);

		BufferedReader br = new BufferedReader(new FileReader(listFilename));
		for (String line; (line=br.readLine()) != null;) {
		    names.add(line);
		}
		br.close();
		
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Whitelist: " + listFilename + " (N=" + names.size() +")");
        }
	}
	@Override
	protected FastqRead filterRead(FastqRead read) {
	    if (names.contains(read.getName())) {
            if (verbose) {
                System.err.println("Kept: " + read.getName());
            }
	        return read;
	    }

	    return null;
	}
}