package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class FlagPresent extends VCFAbstractFilter {
	protected String key;
	
	public FlagPresent(String key) {
		super(generateID(key), generateDescription(key));
		this.key = key;
	}

	private static String generateDescription(String key) {
        return "Contains info flag "+ key;
	}

	private static String generateID(String key) {
		return  key+"_present";
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
	    return record.getInfo().contains(key);
	}
}
