package io.compgen.ngsutils.vcf.vcf;

public class VCFAttributeValue {
	
	public static final VCFAttributeValue MISSING = new VCFAttributeValue(".");
	public static final VCFAttributeValue EMPTY = new VCFAttributeValue("");
	
	protected VCFRecord record;
	
	protected String value;
	
	public VCFAttributeValue(String value) {
		this.value = value;
	}
	
	public String toString() {
		return value;
	}

	public double asDouble() throws VCFAttributeException {
		return asDouble(null);
	}
	
	public int asInt() {
		return Integer.parseInt(value);
	}
	
	public static VCFAttributeValue parse(String s) throws VCFParseException {
		return new VCFAttributeValue(s);
	}

	public String asString(String alleleName) throws VCFAttributeException {
		// TODO: add type to attribute to see if this is a "R" or "A" type
		//       necessary for the nref and alt1 types to know this... for 
		//       now, it's assumed to be "R"

		if (alleleName == null) {
			return value;
		} else {
			String[] spl = value.split(",");
			switch(alleleName) {
			case "ref":
				return spl[0];
			case "alt1":
				return spl[1];
//			case "major":
//				return spl[1];
//				break;
//			case "minor":
//				return spl[1];
//				break;
			default:
				try{
					int i = Integer.parseInt(alleleName);
					return spl[i];
				} catch (NumberFormatException e) {
					throw new VCFAttributeException("Unable to find allele: "+alleleName);
				}
			}
		}
	}

	public double asDouble(String alleleName) throws VCFAttributeException {
		if (alleleName == null) {
			return Double.parseDouble(value);
		} else if (alleleName.equals("sum")) {
			String[] spl = value.split(",");			
			double acc = 0.0;
			for (int i=0; i<spl.length; i++) {
				acc += Double.parseDouble(spl[i]);
			}
			return acc;
		} else if (alleleName.equals("nref")) {
			String[] spl = value.split(",");			
			double acc = 0.0;
			for (int i=1; i<spl.length; i++) {
				acc += Double.parseDouble(spl[i]);
			}
			return acc;
		} else if (alleleName.equals("min")) {
			double minVal = Double.NaN;
			String[] spl = value.split(",");			
			for (int i=1; i<spl.length; i++) {
				double d = Double.parseDouble(spl[i]);
				if (Double.isNaN(minVal) || d < minVal) {
					minVal = d;
				}
			}
			return minVal;
		} else if (alleleName.equals("max")) {
			double maxVal = Double.NaN;
			String[] spl = value.split(",");			
			for (int i=1; i<spl.length; i++) {
				double d = Double.parseDouble(spl[i]);
				if (Double.isNaN(maxVal) || d > maxVal) {
					maxVal = d;
				}
			}
			return maxVal;
		} else {
			String val = asString(alleleName);
			try{
				return Double.parseDouble(val);
			} catch (NumberFormatException e) {
				throw new VCFAttributeException("Invalid value for attribute: \""+val+"\", expected a number.");
			}
		}
	}

}
