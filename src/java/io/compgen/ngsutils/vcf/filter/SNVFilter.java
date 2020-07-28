package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class SNVFilter extends VCFAbstractFilter {
	
	public SNVFilter() {
		super("SNV", "Variant is an SNV");
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
		
		// Variant is an SNV *IIF* ref.len == 1 and *all* alt.len == 1
		
	    if (record.getRef().length()!=1) {
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
