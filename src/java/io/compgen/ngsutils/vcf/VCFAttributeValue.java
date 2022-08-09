package io.compgen.ngsutils.vcf;

public class VCFAttributeValue {
	
	public static final VCFAttributeValue MISSING = new VCFAttributeValue(true, false);
	public static final VCFAttributeValue EMPTY = new VCFAttributeValue(false, true);
	
	final protected String value;

	private VCFAttributeValue(boolean missing, boolean empty) {
		if (missing) {
			this.value = ".";
		} else if (empty) {
			this.value = "";
		} else {
			this.value = null;
		}
	}
	
	public VCFAttributeValue(String value) {
		if (value.equals(MISSING.value)) {
			this.value = MISSING.value;
		} else {
			this.value = value;
		}
	}

	public VCFAttributeValue(int value) {
		this.value = Integer.toString(value);
	}
	public VCFAttributeValue(long value) {
		this.value = Long.toString(value);
	}
	public VCFAttributeValue(float value) {
		this.value = Float.toString(value);
	}
	public VCFAttributeValue(double value) {
		this.value = Double.toString(value);
	}

	public String toString() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	public boolean equals(String val2) {
		return this.value.equals(val2);
	}

	public boolean equals(VCFAttributeValue val2) {
		return this.value.equals(val2.value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VCFAttributeValue other = (VCFAttributeValue) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

    public boolean isMissing() {
        return value.equals(MISSING.value);
    }
    
    public boolean isEmpty() {
        return value.equals(EMPTY.value);
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
		    if (value == null || value.equals("")) {
		        return Double.NaN;
		    }
			return Double.parseDouble(value);
		} else if (alleleName.equals("sum")) {
			String[] spl = value.split(",");			
			double acc = 0.0;
			for (int i=0; i<spl.length; i++) {
                if (!spl[i].equals("")) {
                    acc += Double.parseDouble(spl[i]);
                }
			}
			return acc;
		} else if (alleleName.equals("nref")) {
			String[] spl = value.split(",");			
			double acc = 0.0;
			for (int i=1; i<spl.length; i++) {
                if (!spl[i].equals("")) {
                    acc += Double.parseDouble(spl[i]);
                }
			}
			return acc;
		} else if (alleleName.equals("min")) {
			double minVal = Double.NaN;
			String[] spl = value.split(",");			
			for (int i=0; i<spl.length; i++) {
                if (!spl[i].equals("")) {
    				double d = Double.parseDouble(spl[i]);
    				if (Double.isNaN(minVal) || d < minVal) {
    					minVal = d;
    				}
                }
			}
			return minVal;
		} else if (alleleName.equals("max")) {
			double maxVal = Double.NaN;
			String[] spl = value.split(",");			
			for (int i=0; i<spl.length; i++) {
                if (!spl[i].equals("")) {
    				double d = Double.parseDouble(spl[i]);
    				if (Double.isNaN(maxVal) || d > maxVal) {
    					maxVal = d;
    				}
                }
			}
			return maxVal;
		} else {
			String val = asString(alleleName);
            if (val == null || val.equals("")) {
                return Double.NaN;
            }

			try{
				return Double.parseDouble(val);
			} catch (NumberFormatException e) {
				throw new VCFAttributeException("Invalid value for attribute: \""+val+"\", expected a number.");
			}
		}
	}
}
