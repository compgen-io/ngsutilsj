package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

@MathFilter(id="lt", desc="less than")
public class LessThan extends VCFAbstractFilter {
	protected String key;
	protected double thres;
	protected String sampleName=null;
	protected String alleleName=null;
	
	protected int sampleIdx = -2;
	
	public LessThan(String key, double thres, String sampleName, String alleleName) {
		this(key, thres, sampleName, alleleName, LessThan.class);
	}

	protected LessThan(String key, double thres, String sampleName, String alleleName, Class<?> clazz) {
		super(generateID(key,thres,sampleName, alleleName,clazz), generateDescription(key,thres,sampleName, alleleName, clazz));
		
		if (sampleName != null && sampleName.equals("")) {
			sampleName = null;
		}
		if (alleleName != null && alleleName.equals("")) {
			alleleName = null;
		}
				
		this.key = key;
		this.thres = thres;
		this.sampleName = sampleName;
		this.alleleName = alleleName;
	}

	private static String generateDescription(String key, double thres, String sampleName, String alleleName, Class<?> clazz) {
		String s = key + " " + getDescString(clazz) + " " + thres + " (in ";
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

	private static String generateID(String key, double thres, String sampleName, String alleleName, Class<?> clazz) {
		String s = key + "_" + getIDString(clazz) + "_" + thres;
		if (sampleName != null && !sampleName.equals("")) {
			s += "_"+sampleName;
		}
		
		if (alleleName != null && !alleleName.equals("")) {
			s += "_"+alleleName;			
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
	        return false;
	    }
		double d;
		try {
			d = val.asDouble(alleleName);
		} catch (VCFAttributeException e) {
			throw new VCFFilterException(e);
		}
		return Double.isNaN(d) || operation(d);
		
	}

	protected static String getIDString(Class<?> clazz) {
		MathFilter ann = clazz.getAnnotation(MathFilter.class);
		if (ann != null) {
			return ann.id();
		}
		return null;
	}
	
	protected static String getDescString(Class<?> clazz) {
		MathFilter ann = clazz.getAnnotation(MathFilter.class);
		if (ann != null) {
			return ann.desc();
		}
		return null;
	}
	
	protected boolean operation(double d) {
		return d < thres;	
	}
}
