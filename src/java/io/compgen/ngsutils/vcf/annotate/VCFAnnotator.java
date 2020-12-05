package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public interface VCFAnnotator {
	public VCFRecord next() throws VCFAnnotatorException;
	public void setHeader(VCFHeader header) throws VCFAnnotatorException;
	public void setParent(VCFAnnotator parent) throws VCFAnnotatorException;
	public void close() throws VCFAnnotatorException;
	
    public void setAltChrom(String key) throws VCFAnnotatorException;
    public void setAltPos(String key) throws VCFAnnotatorException;
	public void setEndPos(String endPos) throws VCFAnnotatorException;
}
