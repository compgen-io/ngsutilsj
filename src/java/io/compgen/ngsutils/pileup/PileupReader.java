package io.compgen.ngsutils.pileup;

import io.compgen.common.AbstractLineReader;

import java.io.IOException;
import java.io.InputStream;

public class PileupReader extends AbstractLineReader<PileupRecord>{
    private final int minBaseQual;
	public PileupReader(String fname) throws IOException {
		this(fname,0);
	}
	public PileupReader(InputStream is) {
		this(is,0);
	}

    public PileupReader(InputStream is, int minBaseQual) {
        super(is);
        this.minBaseQual = minBaseQual;
    }
    public PileupReader(String fname, int minBaseQual) throws IOException {
        super(fname);
        this.minBaseQual = minBaseQual;
    }
    @Override
	protected PileupRecord convertLine(String line) {
		if (line == null || line.charAt(0) == '#') {
			return null;
		}
		try {
            return PileupRecord.parse(line, minBaseQual);
		} catch (Exception e) {
			System.err.println(line);
			e.printStackTrace();
			throw e;
		}
	}
}
