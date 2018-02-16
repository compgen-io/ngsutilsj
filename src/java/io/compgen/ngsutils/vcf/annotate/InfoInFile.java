package io.compgen.ngsutils.vcf.annotate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.compgen.ngsutils.vcf.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class InfoInFile extends AbstractBasicAnnotator {

	protected String filename;
	protected String flagName;
	protected String tagName;
	protected Set<String> set = new HashSet<String>();
	
	public InfoInFile(String filename, String tagName, String flagName) throws IOException {
		this.filename = filename;
		this.flagName = flagName;
		this.tagName = tagName;
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = reader.readLine()) != null) {
			set.add(line.trim());
		}
		reader.close();
	}
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.info(flagName, "0", "Flag", "Is value "+tagName+" present in file", filename, null, null, null);
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		header.addFormat(getAnnotationType());
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		VCFAttributeValue ann = record.getInfo().get(tagName);
		if (ann !=null) {
			String val;
			try {
				val = ann.asString(null);
			} catch (VCFAttributeException e) {
				throw new VCFAnnotatorException(e);
			}
			if (!val.equals(VCFAttributeValue.MISSING) && set.contains(val.trim())) {
				record.getInfo().put(flagName, VCFAttributeValue.EMPTY);
			}
		}
	}
}