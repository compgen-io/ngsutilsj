package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public abstract class AbstractBasicAnnotator implements VCFAnnotator {

	private VCFAnnotator parent = null;
	protected abstract void annotate(VCFRecord record) throws VCFAnnotatorException;
	public abstract void setHeader(VCFHeader header) throws VCFAnnotatorException;

	@Override
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException {
		this.parent = parent;
	}

	@Override
	public void close() throws VCFAnnotatorException {
	}

	@Override
	public VCFRecord next() throws VCFAnnotatorException {
		if (parent == null) {
			throw new VCFAnnotatorException("Missing parent in chain!");
		}
		VCFRecord next = parent.next();
		if (next != null) {
			annotate(next);
		}
		return next;
	}
}