package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public interface VCFAnnotator {
	public VCFRecord next() throws VCFAnnotatorException;
	public void setHeader(VCFHeader header) throws VCFAnnotatorException;
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException;
	public void close() throws VCFAnnotatorException;
}
