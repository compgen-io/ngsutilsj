package io.compgen.ngsutils.vcf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.cli.vcf.VCFCheck;

public class VCFRecord {

    public enum VCFSVConnection {
    	NA,
    	FiveToFive,
    	FiveToThree,
    	ThreeToThree,
    	ThreeToFive,
    	NToN,
    	UNK;    	

    	public static VCFSVConnection parse(String val) {
    		if (val.toLowerCase().equals("5to5")) {
    			return FiveToFive;
    		} else if (val.toLowerCase().equals("5to3")) {
    			return FiveToThree;
    		} else if (val.toLowerCase().equals("3to3")) {
    			return ThreeToThree;
    		} else if (val.toLowerCase().equals("3to5")) {
    			return ThreeToFive;
    		} else if (val.toLowerCase().equals("NtoN")) {
    			return NToN;
    		} else {
    			return NA;
    		}
    	}
    	
    	public String toString() {
    		if (this==FiveToFive) {
    			return "5to5";
    		} else if (this==FiveToThree) {
    			return "5to3";
    		} else if (this==ThreeToThree) {
    			return "3to3";
    		} else if (this==ThreeToFive) {
    			return "3to5";
    		} else if (this==NToN) {
    			return "NToN";
    		} else if (this==NA) {
    			return "";
    		}
    		return "UNK";
    	}
	}

    public enum VCFVarType {
    	SNV,
    	BND,
    	DEL,
    	INS,
    	INV,
    	DUP,
    	CNV,
    	UNK;

    	public static VCFVarType parse(String val) {
    		if (val.equals("DEL")) {
    			return DEL;
    		} else if (val.equals("BND")) {
    			return BND;
    		} else if (val.equals("TRA")) {
    			return BND;
    		} else if (val.equals("INS")) {
    			return INS;
    		} else if (val.equals("INV")) {
    			return INV;
    		} else if (val.equals("DUP")) {
    			return DUP;
    		} else if (val.equals("CNV")) {
    			return CNV;
    		} else {
    			return UNK;
    		}
    	}
	}

	public class VCFAltPos {
    	public final String chrom;
    	public final int pos;
    	public final VCFVarType type;
    	public final VCFSVConnection connType;
    	public final String alt;
    	public final String extra;
    	
    	public VCFAltPos(String chrom, int pos, VCFVarType type, VCFSVConnection connType, String alt, String extra) {
    		this.chrom = chrom;
    		this.pos = pos;
    		this.alt = alt;
    		this.extra = extra;
    		
    		if (type != null) {
    			this.type = type;
    		} else {
    			this.type = VCFVarType.SNV;
    		}

    		if (connType != null) {
    			this.connType = connType;
    		} else {
    			this.connType = VCFSVConnection.NA;
    		}
    	}
	}

	// passing filter
	public static final String PASS = "PASS";
	// missing value marker
	public static final String MISSING = ".";
	
	protected String chrom;
	protected int pos; // one-based
	protected String dbSNPID = "";
	protected String ref = ""; // reference base
	protected List<String> alt = null;
	protected String altOrig = null;
	protected double qual = -1;
	protected List<String> filters = null;

	protected VCFAttributes info = null;
	protected List<String> formatKeys = null;
	protected List<VCFAttributes> sampleAttributes = null;

	protected VCFHeader parentHeader = null;
	
	public VCFRecord(String chrom, int pos, String ref) {
		this.chrom = chrom;
		this.pos = pos;
		this.ref = ref;
	}
	
	public VCFRecord(String chrom, int pos, String dbSNPID, String ref, List<String> alt, double qual,
			List<String> filters, VCFAttributes info, List<String> formatKeys, List<VCFAttributes> sampleAttributes, String altFull, VCFHeader header) {
		this.chrom = chrom;
		this.pos = pos;
		this.dbSNPID = dbSNPID;
		this.ref = ref;
		this.alt = alt;
		this.qual = qual;
		this.filters = filters;
		this.info = info;
		this.sampleAttributes = sampleAttributes;
		this.altOrig = altFull;
		this.formatKeys = formatKeys;
		this.parentHeader = header;
	}

    public void write(OutputStream out) throws IOException{
		List<String> outcols = new ArrayList<String>();
		
		outcols.add(chrom);
		outcols.add(""+pos);
		if (dbSNPID == null) {
            outcols.add(MISSING);
		} else {
			outcols.add(dbSNPID);
		}
		outcols.add(ref);
		
		if (alt == null || alt.size() == 0) {
			outcols.add(MISSING);
		} else {
			outcols.add(StringUtils.join(",", alt));
		}
		
		if (qual == -1) {
			outcols.add(MISSING);
		} else {
			String qstr = ""+qual;
			if (qstr.endsWith(".0")) {
				qstr = qstr.substring(0,  qstr.length()-2);
			}
			outcols.add(qstr);
		}

		
        if (filters != null && filters.size() == 0) {
            outcols.add(MISSING);
        } else if (!isFiltered()) {
            outcols.add(PASS);
		} else {
          outcols.add(StringUtils.join(";", filters));
		}
		
        if (info == null) {
        	outcols.add(MISSING);
        } else {
        	outcols.add(info.toString());
        }
        
		if (sampleAttributes != null && sampleAttributes.size() > 0) {
			if (formatKeys != null && formatKeys.size() > 0) {
				// Write FORMAT
	            outcols.add(StringUtils.join(":", formatKeys));
				
				// Write sample values
				for (VCFAttributes attrs: sampleAttributes) {
					if (attrs == null) {
						outcols.add(".");
					} else {
						outcols.add(attrs.toString(formatKeys));
					}
				}
			}
		}
		
		StringUtils.writeOutputStream(out, StringUtils.join("\t", outcols)+"\n");
	}

    public static VCFRecord parseLine(String line) throws VCFParseException {
        return parseLine(line, false, null);
    }
    
    public static VCFRecord parseLine(String line, boolean removeID, VCFHeader header) throws VCFParseException {
		String[] cols = line.split("\t");

		if (cols.length< 5) {
			throw new VCFParseException("Missing columns in VCFRecord! => " + StringUtils.join(",", cols));
		}
		String chrom = cols[0];
		int pos = Integer.parseInt(cols[1]);
		
		String dbSNPID = cols[2];
		if (removeID || dbSNPID.equals(MISSING)) {
			dbSNPID = null;
		}
		
		String ref = cols[3];
		String altOrig = cols[4];
		List<String> alts = null;
		for (String a: cols[4].split(",")) {
			if (!a.equals(MISSING)) {
				if (alts == null) {
					alts = new ArrayList<String>();
				}
				alts.add(a);
			}
		}

		double qual = -1;
		if (cols.length > 5) {
			if (!cols[5].equals(MISSING)) {
				qual = Double.parseDouble(cols[5]);
			}
		}
		
		List<String> filters = null;
		if (cols.length > 6) {
			if (!cols[6].equals(PASS)) {
			    // if filters is null => PASS
			    // if filters is not null, but empty => MISSING ??
	
			    for (String f: cols[6].split(";")) {
	                if (filters == null) {
	                    filters = new ArrayList<String>();
	                }
					if (!f.equals(MISSING) && (header == null || header.isFilterAllowed(f))) {
					    filters.add(f);
					}
				}
			}
		}

//		System.err.println("FILTER: " + cols[6] + " => " + (filters == null ? "<null>": "?"+StringUtils.join(",", filters)));
		List<String> formatKeys = null;

		try {
			List<VCFAttributes> sampleValues = null;
			VCFAttributes info = new VCFAttributes();

			if (cols.length > 7) {
		        info = VCFAttributes.parseInfo(cols[7], header);
			
		//		System.err.println(info.toString());
				
				
				if (cols.length>8) {
					formatKeys = new ArrayList<String>();
					for (String k: cols[8].split(":")) {
						formatKeys.add(k);
					}
					
					if (cols.length>9) {
						sampleValues = new ArrayList<VCFAttributes>(header.getSamples().size());
						
//						System.out.println("header.getSamples().size()=" + header.getSamples().size());
//						System.out.println("sampleValues.size()=" + sampleValues.size());
//
						for (int i=9; i<cols.length; i++) {
							String origSample = header.getOrigSampleName(i - 9);
//							System.out.println("origSample=" + origSample);
							if (origSample != null) {
								int newSampleIdx = header.getSamplePosByName(origSample);
//								System.out.println("newSampleIdx=" + newSampleIdx);
								if (newSampleIdx > -1) {
									while (sampleValues.size() <= newSampleIdx) {
										sampleValues.add(null);
									}
									sampleValues.set(newSampleIdx, VCFAttributes.parseFormat(cols[i], formatKeys, header));
								}
							}
						}
					}
				}
			}
			
			return new VCFRecord(chrom, pos, dbSNPID, ref, alts, qual, filters, info, formatKeys, sampleValues, altOrig, header);

		} catch (VCFParseException | VCFAttributeException e) {
			if (!VCFCheck.isQuiet()) {
	            System.err.println("ERROR: processing VCF record ("+ e + ")");
	            System.err.println("ERROR: " + line);
			}
            System.exit(2);
		}
		
		throw new VCFParseException("Unknown parse exception");
	}

    public String toString() {
    	return chrom + ":" + pos +":"+ref+">"+altOrig;
    }
    
	public String getChrom() {
		return chrom;
	}

	public void setChrom(String chrom) {
		this.chrom = chrom;
	}

	public int getPos() {
		return pos;
	}

	public void setPos(int pos) {
		this.pos = pos;
	}

	public String getDbSNPID() {
		return dbSNPID;
	}

	public void setDbSNPID(String dbSNPID) {
		this.dbSNPID = dbSNPID;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public List<String> getAlt() {
		return alt;
	}

	public void addAlt(String alt) {
		if (this.alt == null) {
			this.alt = new ArrayList<String>();
		}
		this.alt.add(alt);
	}

	public void clearAlt() {
		this.alt = null;
	}

	public String getAltOrig() {
		return altOrig;
	}

	
	public double getQual() {
		return qual;
	}

	public void setQual(int qual) {
		this.qual = qual;
	}

	public List<String> getFilters() {
		return filters;
	}

	public void addFilter(String filter) {
		if (filters == null) {
			filters = new ArrayList<String>();
		}
		filters.add(filter);
	}

	public void clearFilters() {
		this.filters = null;
	}

	public VCFAttributes getInfo() {
		return info;
	}

	public void setInfo(VCFAttributes info) {
		this.info = info;
	}

	public VCFHeader getParentHeader() {
		return parentHeader;
	}
	
	public List<VCFAttributes> getSampleAttributes() {
		return sampleAttributes;
	}

	public VCFAttributes getFormatBySample(String sample) throws VCFAttributeException {
		if (parentHeader == null) {
			throw new VCFAttributeException("Missing header -- cannot extract sample without the header");
		}
		if (sampleAttributes == null) {
			return null;
		}
		
		return sampleAttributes.get(parentHeader.getSamplePosByName(sample));
	}
	
	public void addSampleAttributes(VCFAttributes attrs) {
		if (sampleAttributes == null) {
			sampleAttributes = new ArrayList<VCFAttributes>();
		}
		sampleAttributes.add(attrs);
	}

    public boolean isIndel() {
        if (ref.length() != 1) {
            return true;
        }
        
        if (alt != null) { 
	        	for (String a: alt) {
	            if (a.length() != 1) {
	                return true;
	            }
	        }
        }
        return false;
    }

    public boolean isFiltered() {
        return (filters != null && filters.size() > 0);
    }

	public List<VCFAltPos> getAltPos() {
		return getAltPos(null, null, null, null);
	}

	/** 
	 * 
	 * Parse the Alt values to determine where the end of the variant is (for SVs this is complex)
	 * 
	 * Default INFO keys:
	 * altChromKey = none -- determined from alt
	 * altPosKey = END
	 * typeKey = SVTYPE
	 * connKey = none -- determined from alt (for everything except INV)
	 */
	
	public List<VCFAltPos> getAltPos(String altChromKey, String altPosKey, String typeKey, String connKey) {
		if (altPosKey == null) {
			altPosKey = "END";
		}
		if (typeKey == null) {
			typeKey = "SVTYPE";
		}
		
		List<VCFAltPos> ret = new ArrayList<VCFAltPos>();
		
		if (altChromKey != null) {
			// If there is an alt-chrom key given, only one alt will be returned.
			
			if (getInfo().get(altChromKey) == null || getInfo().get(altPosKey) == null) {
				return ret;
			}
			
			String altChrom = getInfo().get(altChromKey).toString();
            int altPos = getInfo().get(altPosKey).asInt();

			VCFVarType altType = VCFVarType.SNV;
			VCFSVConnection altConn = VCFSVConnection.NA;

            if (typeKey != null && getInfo().get(typeKey) != null) {
	            altType = VCFVarType.parse(getInfo().get(typeKey).toString());
			}
            if (connKey != null && getInfo().get(connKey) != null) {
            	altConn = VCFSVConnection.parse(getInfo().get(connKey).toString());
			}
            
            ret.add(new VCFAltPos(altChrom, altPos, altType, altConn, this.altOrig, null));
            return ret;
		}
		
		for (String alt: getAlt()) {
			VCFVarType altType = VCFVarType.SNV;
			VCFSVConnection altConn = VCFSVConnection.NA;
            String altChrom = this.chrom;
            int altPos = this.pos;
            String extra = null;

        	if (alt.startsWith("[") || alt.startsWith("]") || alt.endsWith("[") || alt.endsWith("]")) {
        		//BND
        		String sub="";
        		if (alt.startsWith("[")) {
        			altType = VCFVarType.BND;
        			altConn = VCFSVConnection.FiveToFive;
        			sub = alt.substring(1,alt.indexOf("[", 1));
        			extra = alt.substring(alt.indexOf("[", 1)+1);
        		} else if (alt.startsWith("]")) {
        			altType = VCFVarType.BND;
        			altConn = VCFSVConnection.FiveToThree;
        			sub = alt.substring(1,alt.indexOf("]", 1));
        			extra = alt.substring(alt.indexOf("]", 1)+1);
        		} else if (alt.endsWith("[")) {
        			altType = VCFVarType.BND;
        			altConn = VCFSVConnection.ThreeToFive;
        			sub = alt.substring(alt.indexOf("[")+1,alt.length()-1);
        			extra = alt.substring(0, alt.indexOf("[", 1));
        		} else if (alt.endsWith("]")) {
        			altType = VCFVarType.BND;
        			altConn = VCFSVConnection.ThreeToThree;
        			sub = alt.substring(alt.indexOf("]")+1,alt.length()-1);
        			extra = alt.substring(0, alt.indexOf("]", 1));
        		}

        		String[] spl = sub.split(":");
        		altChrom = spl[0];
        		altPos = Integer.parseInt(spl[1]);
        		
        		
        	} else if (alt.startsWith("<") && !alt.startsWith("<INS")) {
        		// DEL, DUP, INV, CNV
        		altChrom = chrom;

        		if (alt.startsWith("<CNV")) {
        			altType = VCFVarType.CNV;
        			altConn = VCFSVConnection.NA;
        		} else if (alt.startsWith("<INV")) {
            		altType = VCFVarType.INV;
        			altConn = VCFSVConnection.UNK;
            	} else if (alt.startsWith("<DEL")) {
            		altType = VCFVarType.DEL;
        			altConn = VCFSVConnection.ThreeToFive;
        		} else if (alt.startsWith("<DUP")) { // matches DUP and DUP:TANDEM
            		altType = VCFVarType.DUP;
        			altConn = VCFSVConnection.FiveToThree;
        		}

        		// These must specify an END value
            	altPos = getInfo().get(altPosKey).asInt();

        	} else {
        		// INS or SNV
        		if (alt.startsWith("<INS") || alt.length() > 1) {
            		altChrom = chrom;
        			altPos = pos;
        			altType = VCFVarType.INS;
        			altConn = VCFSVConnection.NToN;
        		} else if (ref.length() > 1) {
        			// this is an in-place deletion
            		altChrom = chrom;
        			altPos = pos + ref.length(); // VCF pos is 1-based... ug...        			
        			altType = VCFVarType.DEL;
        			altConn = VCFSVConnection.ThreeToFive;
        		} else {
            		altChrom = chrom;
            		altPos = pos;
            		altType = VCFVarType.SNV;
        			altConn = VCFSVConnection.NA;
        		}
        	}

        	if (altPosKey != null && getInfo().get(altPosKey)!= null) {
        		altPos = getInfo().get(altPosKey).asInt();
			}
        	
        	if (typeKey != null && getInfo().get(typeKey) != null) {
	            altType = VCFVarType.parse(getInfo().get(typeKey).toString());
			}

        	if (connKey != null && getInfo().get(connKey) != null) {
            	altConn = VCFSVConnection.parse(getInfo().get(connKey).toString());
			} else if (altType == VCFVarType.INV && altConn == VCFSVConnection.UNK && getInfo().get("CT") != null) {
				// if we don't explicitly set connKey, and we couldn't figure it out from the alt value,
				// look for the "CT" INFO field by default. That if that works, try it.
            	altConn = VCFSVConnection.parse(getInfo().get("CT").toString());
			}


            ret.add(new VCFAltPos(altChrom, altPos, altType, altConn, alt, extra));
            
		}
		
		
		return ret;
	}

	/**
	 * 
	 * @param rec
	 * @return -1 if Transition (Ts), 1 if Transversion (Tv), 0 if otherwise ignored (MNV, indel, etc...)
	 */
	public int calcTsTv() {
	    if (getRef().length() != 1) {
	        // ignore indel
	    	return 0;
	    }
	    if (getAlt() == null || getAlt().size() > 1) {
	        // ignore multi-variant positions
	    	return 0;
	    }
	    if (getAlt().get(0).length() != 1) {
	        // ignore indel
	    	return 0;
	    }
	    
	    // this shouldn't happen for variants...
	    if (getRef().toUpperCase().equals(getAlt().get(0).toUpperCase())) {
	    	return 0;
	    }
	    
	               
	    switch (getRef().toUpperCase()) {
	    case "A":
	        if (getAlt().get(0).toUpperCase().equals("G")) {
	        	return -1;
	        } else {
	        	return 1;
	        }
	    case "G":
	        if (getAlt().get(0).toUpperCase().equals("A")) {
	        	return -1;
	        } else {
	        	return 1;
	        }
	    case "C":
	        if (getAlt().get(0).toUpperCase().equals("T")) {
	        	return -1;
	        } else {
	        	return 1;
	        }
	    case "T":
	        if (getAlt().get(0).toUpperCase().equals("C")) {
	        	return -1;
	        } else {
	        	return 1;
	        }
	    }

	    return 0;
	}

	private void writeToStream(OutputStream os, String s) throws IOException {
		os.write((s + "\n").getBytes());
	}
	
    public String dump() throws IOException{
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	
    	writeToStream(baos, chrom+":"+pos+" "+ref+">"+StringUtils.join(",", alt));
    	writeToStream(baos, "ID: " + dbSNPID);
		String qstr = ""+qual;
		if (qstr.endsWith(".0")) {
			qstr = qstr.substring(0,  qstr.length()-2);
		}
		writeToStream(baos, "Qual: " + qstr);
		
        if (filters != null && filters.size() == 0) {
        	writeToStream(baos, "Filters: " + MISSING);
        } else if (!isFiltered()) {
        	writeToStream(baos, "Filters: " + PASS);
		} else {
			writeToStream(baos, "Filters: " + StringUtils.join(";", filters));
		}

        writeToStream(baos, "INFO: ");
        for (String k: info.getKeys()) {
        	writeToStream(baos, "    "+ k + " => " + info.get(k));
        }

        for (int i=0; i< sampleAttributes.size(); i++) {
        	writeToStream(baos, "Sample " + i +":");
	        for (String k: sampleAttributes.get(0).getKeys()) {
	        	writeToStream(baos, "    "+ k + " => " + sampleAttributes.get(i).get(k));
	        }
        }
        return baos.toString();
	}
}
