package io.compgen.ngsutils.vcf.export;

import java.util.List;

import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public interface VCFExport {
	public List<String> getFieldNames();
	public void export(VCFRecord rec, List<String> outs) throws VCFExportException;
	public void setHeader(VCFHeader header);
	public void setMissingValue(String missingValue);
}

