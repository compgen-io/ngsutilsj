package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class FormatValueMissing extends VCFAbstractFilter {
	protected String key;
	protected String sampleName=null;
	
	protected int sampleIdx = -2;
	
    public FormatValueMissing(String key) {
        this(key, null, FormatValueMissing.class);
    }

    public FormatValueMissing(String key, String sampleName) {
        this(key, sampleName, FormatValueMissing.class);
    }

	protected FormatValueMissing(String key, String sampleName, Class<?> clazz) {
		super(generateID(key,sampleName, clazz), generateDescription(key,sampleName, clazz));
		
		if (sampleName != null && sampleName.equals("")) {
			sampleName = null;
		}
				
		this.key = key;
		this.sampleName = sampleName;
	}

	private static String generateDescription(String key, String sampleName, Class<?> clazz) {
		String s = "Missing value: " + key + " (in ";
		if (sampleName == null || sampleName.equals("")) {
			s += "all samples)";
		} else {
			s += "sample: "+sampleName + ")";
		}
		
		
		return s;
	}

    private static String generateID(String key, String sampleName, Class<?> clazz) {
		String s = key + "_missing";
		if (sampleName != null && !sampleName.equals("")) {
			s += "_"+sampleName;
		}
		
		return s;
	}

	public void setHeader(VCFHeader header) throws VCFFilterException {
		super.setHeader(header);
		if (sampleName != null && !sampleName.equals("INFO")) {
			sampleIdx = header.getSamplePosByName(sampleName);
			if (sampleIdx < 0) {
				throw new VCFFilterException("Unable to find sample: "+sampleName);
			}
		}
	}
	
	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
		if (sampleName!=null && sampleName.equals("INFO")) {
			return filter(record.getInfo().get(key));
		}
		if (sampleIdx < 0) {
			// either it was not set or was set as "".
			for (VCFAttributes attr: record.getSampleAttributes()) {
				if (filter(attr.get(key))) {
					return true;
				}
 			}
			return false;
		} else {
			return filter(record.getSampleAttributes().get(sampleIdx).get(key));
		}
	}

	protected boolean filter(VCFAttributeValue val) throws VCFFilterException {
	    if (val == null || val.isMissing()) {
	        return true;
	    }
	    
	    if (this.key.equals("GT")) {
	        // GT field has some special rules...
	        if (val.equals("./.") || val.equals(".|.")) {
	            return true;
	        }
	    }
	    
	    return false;
	}
}
