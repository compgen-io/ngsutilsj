package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class Indel extends AbstractBasicAnnotator {
	public VCFAnnotationDef[] getAnnotationTypes() throws VCFAnnotatorException {
		try {
			return new VCFAnnotationDef[] {
					VCFAnnotationDef.info("CG_INSERT", "0", "Flag", "Variant is an insertion"),
					VCFAnnotationDef.info("CG_DELETE", "0", "Flag", "Variant is an deletion"),
					VCFAnnotationDef.info("CG_INSLEN", "1", "Integer", "Insertion length"),
                    VCFAnnotationDef.info("CG_DELLEN", "1", "Integer", "Deletion length"),
                    VCFAnnotationDef.info("CG_INDELLEN", "1", "Integer", "In-del length"),
					};
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		for (VCFAnnotationDef def : getAnnotationTypes()) {
			header.addFormat(def);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		boolean insert = false;
		boolean deletion = false;
		int insLen = 0;
		int delLen = 0;
		
		if (record.getRef().length()>1) {
			deletion = true;
			delLen = record.getRef().length()-1;
		}
		
		for (String alt: record.getAlt()) {
			if (alt.length()>1) {
				insert = true;
				insLen = Math.max(insLen, alt.length()-1);
			}
		}
		
		try {		
			if (insert) {
				record.getInfo().putFlag("CG_INSERT");
	            record.getInfo().put("CG_INSLEN", new VCFAttributeValue(""+insLen));
	            record.getInfo().put("CG_INDELLEN", new VCFAttributeValue(""+insLen));
			}
			if (deletion) {
				record.getInfo().putFlag("CG_DELETE");
				record.getInfo().put("CG_DELLEN", new VCFAttributeValue(""+delLen));
	            record.getInfo().put("CG_INDELLEN", new VCFAttributeValue("-"+delLen));
			}
		} catch (VCFAttributeException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}