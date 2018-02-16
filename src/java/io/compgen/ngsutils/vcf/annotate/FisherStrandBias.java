package io.compgen.ngsutils.vcf.annotate;

import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.stats.FisherExact;
import io.compgen.ngsutils.vcf.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

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
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		VCFAnnotationDef sacDef = header.getFormatDef("SAC");
		if (sacDef == null || !sacDef.number.equals(".") || !sacDef.type.equals("Integer")) {
			throw new VCFAnnotatorException("\"SAC\" FORMAT annotation missing!");
		}
		header.addFormat(getAnnotationType());
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		for (VCFAttributes sampleVals : record.getSampleAttributes()) {
			// This should be the strand-level counts for each allele (ref+,ref-,alt1+,alt1-,alt2+,alt2-, etc...)
			String sacVal = sampleVals.get("SAC").toString();
			String[] spl = sacVal.split(",");
			int refPlus = Integer.parseInt(spl[0]);
			int refMinus = Integer.parseInt(spl[1]);
			
			List<String> fsbOuts = new ArrayList<String>();
			for (int i=2; i<spl.length; i+=2) {
				int plus = Integer.parseInt(spl[i]);
				int minus = Integer.parseInt(spl[i+1]);
				
				fsbOuts.add(""+round(phred(calcFisherStrandBias(refPlus, refMinus, plus, minus)),3));
			}
			
			if (fsbOuts.size() == 0) {
				sampleVals.put("CG_FSB", VCFAttributeValue.MISSING);	
			} else {
				sampleVals.put("CG_FSB", new VCFAttributeValue(StringUtils.join(",", fsbOuts)));	
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