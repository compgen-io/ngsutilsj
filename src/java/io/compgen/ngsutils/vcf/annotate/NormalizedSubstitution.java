package io.compgen.ngsutils.vcf.annotate;

import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.SeqUtils;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class NormalizedSubstitution extends AbstractBasicAnnotator {
	

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
            header.addInfo(VCFAnnotationDef.info("CG_NORM_SUB", ".", "String", "The normalized substitution (relative to A or C)"));
        } catch (VCFParseException e) {
            throw new VCFAnnotatorException(e);
        }
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		// don't annotate indels
		if (record.getRef().length()>1) {
			return;
		}
		
        List<String> outs = new ArrayList<String>();
		for (String alt: record.getAlt()) {
			// don't annotate indels
			if (alt.length()>1) {
				continue;
			}
			if (record.getRef().equals("A") || record.getRef().equals("C")) {
				outs.add(record.getRef()+">"+alt);
			} else {
				outs.add(SeqUtils.revcomp(record.getRef())+">"+SeqUtils.revcomp(alt));
			}
		}
		
		if (outs.size() > 0) {
			record.getInfo().put("CG_NORM_SUB", new VCFAttributeValue(StringUtils.join(",", outs)));
		}
	}
}