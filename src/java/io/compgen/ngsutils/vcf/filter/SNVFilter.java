package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class SNVFilter extends VCFAbstractFilter {
	
	public SNVFilter() {
		super("SNV", "Variant is an SNV");
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
	    if (record.getRef().length()==1) {
	        return false;
	    }
	    for (String alt: record.getAlt()) {
	        if (alt.length()!=1) {
	            return false;
	        }
	    }
	    
	    return true;
	}
}
