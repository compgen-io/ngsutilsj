package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFFilterDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public interface VCFFilter {
	public VCFFilterDef getDefinition();
	public void filter(VCFRecord record) throws VCFFilterException;
	public void close();
	public String getID();
	public String getDescription();
	public void setHeader(VCFHeader header) throws VCFFilterException;
}
