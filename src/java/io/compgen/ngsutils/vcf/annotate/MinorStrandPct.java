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

public class MinorStrandPct extends AbstractBasicAnnotator {
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.format("CG_SBPCT", "A", "Float", "Percent of alt-allele reads on the minor strand");
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		VCFAnnotationDef sacDef = header.getFormatDef("SAC");
		if (sacDef == null || !sacDef.number.equals(".") || !sacDef.type.equals("Integer")) {
			throw new VCFAnnotatorException("\"SAC\" FORMAT annotation missing!");
		}
		header.addFormat(getAnnotationType());
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {	
		for (VCFAttributes sampleVals : record.getSampleAttributes()) {
		    if (sampleVals.contains("SAC")) {
	            // This should be the strand-level counts for each allele (ref+,ref-,alt1+,alt1-,alt2+,alt2-, etc...)
    			String sacVal = sampleVals.get("SAC").toString();
    			String[] spl = sacVal.split(",");
    			
    			List<String> outs = new ArrayList<String>();
    			for (int i=2; i<spl.length; i+=2) {
    				int plus = Integer.parseInt(spl[i]);
    				int minus = Integer.parseInt(spl[i+1]);
    				
    				if (plus + minus == 0) {
    					outs.add("0.0");
    				} else if (plus > minus) {
    					outs.add(""+FisherStrandBias.round(((double)minus) / (plus+minus), 3));
    				} else {
    					outs.add(""+FisherStrandBias.round(((double)plus) / (plus+minus), 3));
    				}
    			}
    			try {
	    			if (outs.size() == 0) {
	    				sampleVals.put("CG_SBPCT", VCFAttributeValue.MISSING);	
	    			} else {
	    				sampleVals.put("CG_SBPCT", new VCFAttributeValue(StringUtils.join(",", outs)));	
	    			}
    			} catch (VCFAttributeException e) {
    				throw new VCFAnnotatorException(e);
    			}
		    }
		}
	}
}