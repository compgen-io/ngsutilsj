package io.compgen.ngsutils.pileup;

import java.io.IOException;
import java.io.InputStream;

import io.compgen.common.AbstractLineReader;

public class PileupReader extends AbstractLineReader<PileupRecord>{
    private final int minBaseQual;
    private final boolean nogaps;
    
	public PileupReader(String fname) throws IOException {
		this(fname,0, false);
	}
	public PileupReader(InputStream is) {
		this(is,0, false);
	}
    public PileupReader(InputStream is, int minBaseQual) {
        this(is, minBaseQual, false);
    }
    public PileupReader(String fname, int minBaseQual) throws IOException {
        this(fname, minBaseQual, false);
    }
    public PileupReader(InputStream is, int minBaseQual, boolean nogaps) {
        super(is);
        this.minBaseQual = minBaseQual;
        this.nogaps = nogaps;
    }
    public PileupReader(String fname, int minBaseQual, boolean nogaps) throws IOException {
        super(fname);
        this.minBaseQual = minBaseQual;
        this.nogaps = nogaps;
    }
    @Override
	protected PileupRecord convertLine(String line) {
		if (line == null || line.charAt(0) == '#') {
			return null;
		}
		try {
            return PileupRecord.parse(line, minBaseQual, nogaps);
		} catch (Exception e) {
			System.err.println(line);
			e.printStackTrace();
			throw e;
		}
	}
}
