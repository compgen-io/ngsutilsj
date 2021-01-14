package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class TransitionTransversion extends AbstractBasicAnnotator {
	

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
            header.addInfo(VCFAnnotationDef.info("CG_TSTV", "1", "String", "Is the variant and transition (TS) or transversion (TV), skips all multi-variants and indels"));
        } catch (VCFParseException e) {
            throw new VCFAnnotatorException(e);
        }
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		int tstv = record.calcTsTv();
		
		if (tstv == -1) {
			record.getInfo().put("CG_TSTV", new VCFAttributeValue("TS"));
		} else if (tstv == 1) {
			record.getInfo().put("CG_TSTV", new VCFAttributeValue("TV"));

		}
	}
}