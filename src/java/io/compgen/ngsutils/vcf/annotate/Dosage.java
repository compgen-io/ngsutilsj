package io.compgen.ngsutils.vcf.annotate;

import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class Dosage extends AbstractBasicAnnotator {
	

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
            header.addFormat(VCFAnnotationDef.format("CG_DS", "A", "Integer", "Convert GT to dosage value (0, 1, 2)"));
        } catch (VCFParseException e) {
            throw new VCFAnnotatorException(e);
        }
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		for (VCFAttributes sampleVals : record.getSampleAttributes()) {
		    if (sampleVals.contains("GT")) {
		    	try {
		    		List<String> dsOut = new ArrayList<String>();
					String[] gts = sampleVals.get("GT").asString(null).split("[/\\|]");
			    	for (int altNum = 1; altNum <= record.getAlt().size(); altNum++) {
				    	int ds = 0;
			    		String alt = ""+altNum;
			    		for (String gt: gts) {
			    			if (gt.equals(alt)) {
			    				ds++;
			    			}
			    		}
			    		dsOut.add(""+ds);
			    	}
			    	sampleVals.put("CG_DS", VCFAttributeValue.parse(StringUtils.join(",", dsOut)));
				} catch (VCFAttributeException | VCFParseException e) {
					throw new VCFAnnotatorException(e);
				}		    	
		    } else {
		    	try {
		    		sampleVals.put("CG_DS", VCFAttributeValue.MISSING);
				} catch (VCFAttributeException e) {
					throw new VCFAnnotatorException(e);
				}
		    }
		}
	}
}