package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class FlagAbsent extends VCFAbstractFilter {
	protected String key;
	
	public FlagAbsent(String key) {
		super(generateID(key), generateDescription(key));
		this.key = key;
	}

	private static String generateDescription(String key) {
        return "Missing flag "+ key;
	}

	private static String generateID(String key) {
		return  key+"_absent";
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
	    return !record.getInfo().contains(key);
	}
}
