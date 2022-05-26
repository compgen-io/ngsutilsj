package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFRecord;

public class MaxDel extends VCFAbstractFilter {
	protected int thres;
	
	public MaxDel(int thres) {
		super(generateID(thres), generateDescription(thres));
		this.thres = thres;
	}

	private static String generateDescription(int thres) {
        return "Deletion longer than "+thres;
	}

	private static String generateID(int thres) {
		return "DEL_max_"+thres;
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
		
    	// deletions are shown in the ref value
		//
		// ACCCCC -> A is a 5bp deletion
		
	    if (record.getRef().length()-1 > thres) {
	    	return true;
	    }
	    
	    return false;

	}
}
