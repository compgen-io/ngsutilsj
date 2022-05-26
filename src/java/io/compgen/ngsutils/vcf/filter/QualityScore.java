package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class QualityScore extends VCFAbstractFilter {
	protected double thres;
	
	public QualityScore(double thres) {
		super(generateID(thres), generateDescription(thres));
		this.thres = thres;
	}

	private static String generateDescription(double thres) {
        return "Quality score less than "+thres;
	}

	private static String generateID(double thres) {
		return "QUAL_lt_"+thres;
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
	    return (record.getQual() < thres);
	}
}
