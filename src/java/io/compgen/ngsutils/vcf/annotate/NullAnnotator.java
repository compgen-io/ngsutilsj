package io.compgen.ngsutils.vcf.annotate;

import java.util.Iterator;

import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class NullAnnotator implements VCFAnnotator {

	final private Iterator<VCFRecord> it;
	final private boolean onlyPassing;

	public NullAnnotator(VCFReader reader, boolean onlyPassing) {
		this.it = reader.iterator();
		this.onlyPassing = onlyPassing;
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
	}

	@Override
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException {
	}

	@Override
	public VCFRecord next() throws VCFAnnotatorException {
		while (it.hasNext()) {
			VCFRecord rec = it.next();
			if (!rec.isFiltered() || !onlyPassing) {
				return rec;
			}
		}
		return null;
	}

	@Override
	public void close() throws VCFAnnotatorException {
	}

    @Override
    public void setAltChrom(String key) throws VCFAnnotatorException {
    }

    @Override
    public void setAltPos(String key) throws VCFAnnotatorException {
    }
}