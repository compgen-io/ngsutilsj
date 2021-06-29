package io.compgen.ngsutils.vcf.annotate;

import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.stats.FisherExact;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class FisherStrandBias extends AbstractBasicAnnotator {
	protected FisherExact fisher = new FisherExact();

	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.format("CG_FSB", "A", "Float", "Sample-based Fisher Strand Bias for alt alleles (Phred-scale)");
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

    // 2019-03-03 - mbreese
    // changed this test to return the strand bias of *ONLY* the alt allele.
	// the Fisher test is against a theoretical 50%/50% split
	
	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		for (VCFAttributes sampleVals : record.getSampleAttributes()) {
		    if (sampleVals.contains("SAC")) {
	            // This should be the strand-level counts for each allele (ref+,ref-,alt1+,alt1-,alt2+,alt2-, etc...)
    			String sacVal = sampleVals.get("SAC").toString();
    			String[] spl = sacVal.split(",");
    			
//    			int refPlus = Integer.parseInt(spl[0]);
//    			int refMinus = Integer.parseInt(spl[1]);
    			
    			List<String> fsbOuts = new ArrayList<String>();
    			for (int i=2; i<spl.length; i+=2) {
    				int plus = Integer.parseInt(spl[i]);
    				int minus = Integer.parseInt(spl[i+1]);
    				
    				int total = plus + minus;
    				int half = total / 2; // this will round down in cases where total is odd.
    				
    				fsbOuts.add(""+round(phred(calcFisherStrandBias(half, half, plus, minus)),3));
    			}
    			
				try {
	    			if (fsbOuts.size() == 0) {
							sampleVals.put("CG_FSB", VCFAttributeValue.MISSING);
	    			} else {
	    				sampleVals.put("CG_FSB", new VCFAttributeValue(StringUtils.join(",", fsbOuts)));	
	    			}
				} catch (VCFAttributeException e) {
					throw new VCFAnnotatorException(e);
				}	
		    }
		}
	}

	public static String round(double val, int places) {
		return String.format("%."+places+"f", val);
	}

	private double phred(double val) {
		if (val <= 0.0) {
			return 255.0;
		} else if (val >= 1.0) {
			return 0.0;
		} else {
			return -10 * Math.log10(val);
		}
	}

	private double calcFisherStrandBias(int refPlus, int refMinus, int plus, int minus) {
		return fisher.calcTwoTailedPvalue(refPlus, refMinus, plus, minus);
	}
}