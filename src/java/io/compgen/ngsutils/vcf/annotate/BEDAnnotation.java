package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.BedAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.vcf.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class BEDAnnotation extends AbstractBasicAnnotator {
	private static Map<String, BedAnnotationSource> cache = new HashMap<String, BedAnnotationSource>();
	
	final protected String name;
	final protected String filename;
	final protected BedAnnotationSource bed;
	final protected boolean flag;
	
	public BEDAnnotation(String name, String filename, boolean flag) throws IOException {
		this.name = name;
		this.filename = filename;
		this.flag = flag;
		this.bed = getBEDSource(filename);
	}

	private static BedAnnotationSource getBEDSource(String filename) throws IOException {
		if (!cache.containsKey(filename)) {
			cache.put(filename, new BedAnnotationSource(filename));
		}
		return cache.get(filename);		
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		try {
			if (flag) {
				header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in BED file: "+ name, filename, null, null, null));
			} else {
				header.addInfo(VCFAnnotationDef.info(name, "1", "String", "BED file annotation: "+ name, filename, null, null, null));
			}
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		GenomeSpan pos = new GenomeSpan(record.getChrom(), record.getPos());
		List<String> geneNames = new ArrayList<String>();

		for (BedRecord rec : bed.findAnnotation(pos)) {
			geneNames.add(rec.getName());
		}
		
		if (flag) {
			if (geneNames.size() > 0) {
				record.getInfo().put(name, VCFAttributeValue.EMPTY);
			}
		} else {		
			if (geneNames.size() == 0) {
				record.getInfo().put(name, VCFAttributeValue.MISSING);
			} else {
				record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", geneNames)));
			}
		}
	}
}