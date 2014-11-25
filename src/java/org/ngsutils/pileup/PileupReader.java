package org.ngsutils.pileup;

import java.io.IOException;
import java.io.InputStream;

import org.ngsutils.support.AbstractLineReader;

public class PileupReader extends AbstractLineReader<PileupRecord>{
	public PileupReader(String fname) throws IOException {
		super(fname);
	}
	public PileupReader(InputStream is) {
		super(is);
	}

	@Override
	protected PileupRecord convertLine(String line) {
		if (line == null || line.charAt(0) == '#') {
			return null;
		}
		try {
            return PileupRecord.parse(line);
		} catch (Exception e) {
			System.err.println(line);
			e.printStackTrace();
			throw e;
		}
	}
}
