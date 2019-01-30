package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class IndelFilter extends VCFAbstractFilter {
	
	public IndelFilter() {
		super("INDEL", "Variant is an indel");
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
	    if (record.getRef().length()!=1) {
	        return true;
	    }
	    for (String alt: record.getAlt()) {
	        if (alt.length()!=1) {
	            return true;
	        }
	    }
	    
	    return false;
	}
}
