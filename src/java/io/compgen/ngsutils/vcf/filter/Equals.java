package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class Equals extends VCFAbstractFilter {
	protected String key;
	protected String val;
	protected String sampleName=null;
	protected String alleleName=null;
	
	protected int sampleIdx = -2;
	
	public Equals(String key, String val, String sampleName, String alleleName) {
		this(key, val, sampleName, alleleName, Equals.class);
	}

	protected Equals(String key, String val, String sampleName, String alleleName, Class<?> clazz) {
		super(generateID(key,val,sampleName, alleleName,clazz), generateDescription(key,val,sampleName, alleleName, clazz));
		
		if (sampleName != null && sampleName.equals("")) {
			sampleName = null;
		}
		if (alleleName != null && alleleName.equals("")) {
			alleleName = null;
		}
		
		this.key = key;
		this.val = val;
		this.sampleName = sampleName;
		this.alleleName = alleleName;
	}

	private static String generateDescription(String key, String val, String sampleName, String alleleName, Class<?> clazz) {
		String s = key + " equals " + val + " (in ";
		if (sampleName == null || sampleName.equals("")) {
			s += "all samples";
		} else {
			s += "sample: "+sampleName;
		}
		
		if (alleleName == null || alleleName.equals("")) {
			s += ")";
		} else {
			s += ", allele: "+alleleName+")";			
		}
		
		return s;
	}

	private static String generateID(String key, String val, String sampleName, String alleleName, Class<?> clazz) {
		String s = key + "_eq_" + val;
		if (sampleName != null && !sampleName.equals("")) {
			s += "_"+sampleName;
		}
		
		if (alleleName != null && !alleleName.equals("")) {
			s += "_"+alleleName;			
		}
		
		return s.replaceAll(",", "_")
				.replaceAll(";", "_")
				.replaceAll("\\>", "_")
				.replaceAll("\\<", "_")
				.replaceAll(" ", "_")
				.replaceAll("\\t", "_")
				.replaceAll("\\r", "_")
				.replaceAll("\\n", "_")
				.replaceAll("_+", "_");
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
		try {
			if (alleleName != null && alleleName.equals("sum")) {
				double d = val.asDouble(alleleName);
				double test = Double.parseDouble(this.val);
				return d == test;
			}
			
			if (val == null) {
				if (this.val == null || this.val.equals("")) {
					return true;
				}
                return false;
            }

			return this.val.equals(val.asString(alleleName));
		} catch (VCFAttributeException e) {
			throw new VCFFilterException(e);
		}
	}
}
