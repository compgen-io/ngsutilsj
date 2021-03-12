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
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class BEDAnnotation extends AbstractBasicAnnotator {
	private static Map<String, BedAnnotationSource> cache = new HashMap<String, BedAnnotationSource>();
	
	final protected String name;
	final protected String filename;
	final protected BedAnnotationSource bed;
    final protected boolean flag;
    final protected boolean isNumber;
	
	public BEDAnnotation(String name, String filename, boolean flag) throws IOException {
	    if (name.endsWith(",n")) {
            this.isNumber = true;
	        this.name = name.substring(0, name.length()-2);
	    } else {
            this.isNumber = false;
	        this.name = name;
	    }
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
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
			if (flag) {
				header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in BED file: "+ name, filename, null, null, null));
			} else {
			    if (isNumber) {
                    header.addInfo(VCFAnnotationDef.info(name, "1", "Float", "BED file annotation: "+ name, filename, null, null, null));
                } else {
                    header.addInfo(VCFAnnotationDef.info(name, "1", "String", "BED file annotation: "+ name, filename, null, null, null));
                }
			}
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
	    GenomeSpan pos;
        
	    try {
	        pos = new GenomeSpan(getChrom(record), getPos(record)-1, getEndPos(record)); // VCF pos are 1-based
	    } catch (VCFAnnotatorMissingAltException ex) {
	        return;
	    }

        List<String> bedNames = new ArrayList<String>();
		for (BedRecord rec : bed.findAnnotation(pos)) {
			bedNames.add(rec.getName());
		}
		
		if (flag) {
			if (bedNames.size() > 0) {
				record.getInfo().putFlag(name);
			}
		} else {		
			if (bedNames.size() > 0) {
				// don't add an empty annotation
//				record.getInfo().put(name, VCFAttributeValue.MISSING);
//			} else {
				try {
					record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", bedNames)));
				} catch (VCFAttributeException e) {
					throw new VCFAnnotatorException(e);
				}
			}
		}
	}
}