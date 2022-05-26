package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class MaxIns extends VCFAbstractFilter {
	protected int thres;
	
	public MaxIns(int thres) {
		super(generateID(thres), generateDescription(thres));
		this.thres = thres;
	}

	private static String generateDescription(int thres) {
        return "Insertion longer than "+thres;
	}

	private static String generateID(int thres) {
		return "INS_max_"+thres;
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
		
		// Insertions are shown in the alt record
		//
		// A -> ACCCCC is a 5bp insertion
		
		
	    for (String alt: record.getAlt()) {
	        if (alt.length()-1 > thres) {
	            return true;
	        }
	    }
	    
	    return false;

	}
}
