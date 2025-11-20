package io.compgen.ngsutils.vcf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.GlobUtils;

public class VCFHeader {
	protected String fileformat;
	protected Map<String,VCFAnnotationDef> infoDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFAnnotationDef> formatDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFFilterDef> filterDefs = new LinkedHashMap<String, VCFFilterDef>();
	protected List<String> lines = new ArrayList<String>();

	protected Map<String, VCFContigDef> contigDefs = new LinkedHashMap<String, VCFContigDef>();
	protected Map<String, VCFAltDef> altDefs = new LinkedHashMap<String, VCFAltDef>();
	
	protected String headerLine;
	
	protected List<String> origSamples = new ArrayList<String>();
	protected List<String> samples = new ArrayList<String>();
	protected Map<String, Integer> newSampleOrder = new HashMap<String, Integer>();

    private Set<String> removeFilter = null;
    private Set<String> removeInfo = null;
    private Set<String> removeFormat = null;
    private Set<String> keepFilter = null;
    private Set<String> keepInfo = null;
    private Set<String> keepFormat = null;
//    private Set<String> removeSample = null;

    private Set<String> allowedFilterCache = new HashSet<String>();
    private Set<String> blockedFilterCache = new HashSet<String>();
    private Set<String> allowedInfoCache = new HashSet<String>();
    private Set<String> blockedInfoCache = new HashSet<String>();
    private Set<String> allowedFormatCache = new HashSet<String>();
    private Set<String> blockedFormatCache = new HashSet<String>();
    
	public VCFHeader() {
		this("fileformat=VCFv4.2");
		// nothing set...
	}
		
	public VCFHeader(String fileformat) {
		this.fileformat = fileformat;
		// nothing set...
	}

    
	public VCFHeader(String fileformat, List<String> input, String headerLine, Set<String> removeFilter, Set<String> removeInfo, Set<String> removeFormat, Set<String> removeSample, Set<String> keepFilter, Set<String> keepInfo, Set<String> keepFormat, Set<String> keepSample) throws VCFParseException {
		if (fileformat == null) {
			throw new VCFParseException("Missing format in header?");
		}

		this.fileformat = fileformat;
		this.headerLine = headerLine;
		
        this.removeFilter = removeFilter;
        this.removeInfo = removeInfo;
        this.removeFormat = removeFormat;
        this.keepFilter = keepFilter;
        
        this.keepInfo = keepInfo;
        
        this.keepFormat = keepFormat;
//        this.removeSample = removeSample;
		
		for (String line: input) {
			if (line.startsWith("##INFO=")) {
			    VCFAnnotationDef info = VCFAnnotationDef.parseString(line);
                boolean match = false;
                if (removeInfo != null) {
                    for (String remove: removeInfo) {
                        if (GlobUtils.matches(info.id, remove)) {
                            match = true;
    	                    if (keepInfo != null) {
    	                    	for (String keep: keepInfo) {
    	    	                    if (GlobUtils.matches(info.id, keep)) {
    	    	                        match = false;
    	    	                    }    	                    		
    	                    	}
    	                    }
                        }
                    }
                }
                if (!match) {
                    addInfo(info);
                }
			} else if (line.startsWith("##FORMAT=")) {
	             VCFAnnotationDef format = VCFAnnotationDef.parseString(line);
	                boolean match = false;
	                if (removeFormat != null) {
    	                for (String remove: removeFormat) {
    	                    if (GlobUtils.matches(format.id, remove)) {
    	                        match = true;
        	                    if (keepFormat != null) {
        	                    	for (String keep: keepFormat) {
        	    	                    if (GlobUtils.matches(format.id, keep)) {
        	    	                        match = false;
        	    	                    }    	                    		
        	                    	}
        	                    }
    	                    }
    	                }
	                }
	                if (!match) {
	                    addFormat(format);
	                }

				addFormat(VCFAnnotationDef.parseString(line));
            } else if (line.startsWith("##FILTER=")) {
                VCFFilterDef filter = VCFFilterDef.parse(line);
                boolean match = false;
                if (removeFilter != null) {
                    for (String remove: removeFilter) {
                        if (GlobUtils.matches(filter.id, remove)) {
                            match = true;
    	                    if (keepFilter != null) {
    	                    	for (String keep: keepFilter) {
    	    	                    if (GlobUtils.matches(filter.id, keep)) {
    	    	                        match = false;
    	    	                    }    	                    		
    	                    	}
    	                    }
                        }
                    }
                }
                if (!match) {
                    addFilter(filter);
                }
            } else if (line.startsWith("##SAMPLE=")) {
    			Map<String, String> vals = VCFHeader.parseQuotedLine(line.substring(10, line.length()-1));

                boolean match = false;
                if (removeSample != null) {
                    for (String remove: removeSample) {
                        if (GlobUtils.matches(vals.get("ID"), remove)) {
                            match = true;
    	                    if (keepSample != null) {
    	                    	for (String keep: keepSample) {
    	    	                    if (GlobUtils.matches(vals.get("ID"), keep)) {
    	    	                        match = false;
    	    	                    }    	                    		
    	                    	}
    	                    }
                        }
                    }
                }
                if (!match) {
                	lines.add(line);
                }
            } else if (line.startsWith("##contig=")) {
                VCFContigDef contig = VCFContigDef.parse(line);
                addContig(contig);
            } else if (line.startsWith("##ALT=")) {
                VCFAltDef contig = VCFAltDef.parse(line);
                addAlt(contig);
			} else {
				lines.add(line);
			}
		}
		
		String[] spl = headerLine.split("\t");
		
		if (spl.length > 9) {
//			List<String> sampleList = new ArrayList<String>();
			for (int i=9; i< spl.length; i++) {
                boolean removeMe = false;
                if (removeSample != null) {
                    for (String remove: removeSample) {
                        if (GlobUtils.matches(spl[i], remove)) {
                            removeMe = true;
    	                    if (keepSample != null) {
    	                    	for (String keep: keepSample) {
    	    	                    if (GlobUtils.matches(spl[i], keep)) {
    	    	                        removeMe = false;
    	    	                    }    	                    		
    	                    	}
    	                    }
                        }
                    }
                }
                origSamples.add(spl[i]);
                if (!removeMe) {
                	addSample(spl[i]);
//                	sampleList.add(spl[i]);
//    				samples[i-9]=spl[i];
                }
			}
			
//			samples = sampleList.toArray(new String[] {});
		}
	}

	public VCFHeader clone() {
		return clone(false);
	}

	public VCFHeader clone(boolean ignoreSamples) {
		VCFHeader newh = new VCFHeader(this.fileformat);
		
		for (String id: infoDefs.keySet()) {
			newh.infoDefs.put(id, infoDefs.get(id).clone());
		}
		for (String id: formatDefs.keySet()) {
			newh.formatDefs.put(id, formatDefs.get(id).clone());
		}
		for (String id: filterDefs.keySet()) {
			newh.filterDefs.put(id, filterDefs.get(id).clone());
		}
		for (String id: contigDefs.keySet()) {
			newh.contigDefs.put(id, contigDefs.get(id).clone());
		}
		for (String id: altDefs.keySet()) {
			newh.altDefs.put(id, altDefs.get(id).clone());
		}
		
		newh.headerLine = this.headerLine;
		newh.lines.addAll(this.lines);
		if (!ignoreSamples) {
			newh.samples.addAll(this.samples);
		}
		
		
		return newh;
	}

	
	public void addSample(String sample) {
		this.newSampleOrder.put(sample, this.samples.size());
		this.samples.add(sample);
	}
	
	public String getOrigSampleName(int idx) {
		if (idx > -1 && idx < origSamples.size()) {
			return origSamples.get(idx);
		}
		return null;
	}
	
	public List<String> getSamples() {
		return Collections.unmodifiableList(this.samples);
	}
		
	public void renameSample(String oldname, String newname) throws VCFParseException {
		int idx = getSamplePosByName(oldname);
		if (idx > -1) {
			samples.set(idx, newname);
		} else {
			throw new VCFParseException("Couldn't find sample: "+oldname);
		}
	}
	
	/**
	 * Line should include the "##" prefix
	 * @param line
	 */
	public void addLine(String line) {
		this.lines.add(line);
	}
	public List<String> getLines() {
		return Collections.unmodifiableList(this.lines);
	}
	
	public void write(OutputStream out, boolean includeAll) throws IOException {

		if (includeAll) {
			while (!fileformat.startsWith("##")) {
				fileformat = "#" + fileformat; 
			}
			StringUtils.writeOutputStream(out, fileformat + "\n");
		    Calendar now = Calendar.getInstance();
		    now.get(Calendar.YEAR);
			StringUtils.writeOutputStream(out, "##fileDate="+now.get(Calendar.YEAR)+String.format("%02d", now.get(Calendar.MONTH)+1)+String.format("%02d", now.get(Calendar.DATE))+"\n");
		}
		List<String> outlines = new ArrayList<String>();;
		
        for (VCFAnnotationDef def: infoDefs.values()) {
            outlines.add(def.toString());
        }
        for (VCFFilterDef def: filterDefs.values()) {
            outlines.add(def.toString());
        }
        for (VCFAnnotationDef def: formatDefs.values()) {
            outlines.add(def.toString());
        }
        for (VCFContigDef def: contigDefs.values()) {
            outlines.add(def.toString());
        }
        for (VCFAltDef def: altDefs.values()) {
            outlines.add(def.toString());
        }
        outlines.addAll(lines);

		for (String line: outlines) {
			StringUtils.writeOutputStream(out, line + "\n");
		}

		if (includeAll) {
			String header = "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO";
			
			if (samples != null && samples.size()>0) {
				header += "\tFORMAT"; // format only is set when there are samples...
				for (String sample: samples) {
					header = header + "\t" + sample;
				}
			}

			StringUtils.writeOutputStream(out,  header + "\n");
			
//			if (!headerLine.startsWith("#")) {
//				headerLine = "#" + headerLine; 
//			}
//			StringUtils.writeOutputStream(out, headerLine + "\n");
		}
	}

	public boolean contains(String s) {
		for (String line: lines) {
			if (line.equals(s)) {
				return true;
			}
		}
		return false;
	}
	
	/** 
	 * Returns index of a sample by name
	 * @param name - name or number (1,2,3) for a sample
	 * @return 0-based index for a sample in the VCF file.
	 */
	public int getSamplePosByName(String name) {
	    
        // By name NORMAL, TUMOR, etc...
		// we do this check first, because the sample can also be named "123"
		// if this is a valid name, we want to use that.
		//
		// only if that fails do we want to try to convert to a number
		
		if (newSampleOrder != null) {
			if (newSampleOrder.containsKey(name)) {
				return newSampleOrder.get(name);
			} else { 
				return -1;
			}
		} else {
			for (int i=0; i<samples.size(); i++) {
				if (samples.get(i).equals(name)) {
					return i;
				}
			}
		}

		// OR You can ID a sample by index (1, 2, 3)
	    try {
	        int i = Integer.parseInt(name);
	        return i - 1;
	        
	    } catch (NumberFormatException e) {
	        // ignore this...
	    }
	    
		return -1;
	}

	public void addInfo(VCFAnnotationDef def) {
		infoDefs.put(def.id, def);
	}
	public void addFormat(VCFAnnotationDef def) {
		formatDefs.put(def.id, def);
	}
    public void addFilter(VCFFilterDef def) {
        filterDefs.put(def.id, def);
    }
    
    public void addContig(VCFContigDef def) {
        contigDefs.put(def.id, def);
    }
    
    public void removeContig(String id) {
        contigDefs.remove(id);
    }
    
    public VCFContigDef getContigDef(String id) {
        return contigDefs.get(id);
    }
    
    public void addAlt(VCFAltDef def) {
        altDefs.put(def.id, def);
    }
    
    public void removeAlt(String id) {
    	altDefs.remove(id);
    }
    public VCFAltDef getAltDef(String id) {
        return altDefs.get(id);
    }
    
    

	public VCFAnnotationDef getFormatDef(String id) {
		return formatDefs.get(id);
	}

    public Set<String> getFormatIDs() {
        return formatDefs.keySet();
    }
    public Set<String> getFilterIDs() {
        return filterDefs.keySet();
    }

    public VCFFilterDef getFilterDef(String id) {
        return filterDefs.get(id);
    }

    public VCFAnnotationDef getInfoDef(String id) {
        return infoDefs.get(id);
    }

    public Set<String> getInfoIDs() {
        return infoDefs.keySet();
    }

    public void clearInfoDefs() {
    	infoDefs.clear();
    }
    
    public void clearFormatDefs() {
    	formatDefs.clear();
    }
    
    public boolean isFilterAllowed(String name) {
        if (removeFilter == null || removeFilter.size()==0) {
            return true;
        }
        if (allowedFilterCache.contains(name)) {
            return true;
        }
        if (blockedFilterCache.contains(name)) {
            return false;
        }
        for (String remove: removeFilter) {
            if (GlobUtils.matches(name, remove)) {
            	if (keepFilter!=null) {
            		for (String keep: keepFilter) {
            			if (GlobUtils.matches(name, keep)) {
            				allowedFilterCache.add(name);
            		        return true;
            			}
            		}
            	}
                blockedFilterCache.add(name);
                return false;
            }
        }

        allowedFilterCache.add(name);
        return true;
    }
    
    public boolean isInfoAllowed(String name) {
        if (removeInfo == null || removeInfo.size()==0) {
            return true;
        }
        if (allowedInfoCache.contains(name)) {
            return true;
        }
        if (blockedInfoCache.contains(name)) {
            return false;
        }
        for (String remove: removeInfo) {
            if (GlobUtils.matches(name, remove)) {
            	if (keepInfo!=null) {
            		for (String keep: keepInfo) {
            			if (GlobUtils.matches(name, keep)) {
            				allowedInfoCache.add(name);
            		        return true;
            			}
            		}
            	}
                blockedInfoCache.add(name);
                return false;
            }
        }

        allowedInfoCache.add(name);
        return true;
    }
    
    public boolean isFormatAllowed(String name) {
        if (removeFormat == null || removeFormat.size()==0) {
            return true;
        }
        if (allowedFormatCache.contains(name)) {
            return true;
        }
        if (blockedFormatCache.contains(name)) {
            return false;
        }
        for (String remove: removeFormat) {
            if (GlobUtils.matches(name, remove)) {
            	if (keepFormat!=null) {
            		for (String keep: keepFormat) {
            			if (GlobUtils.matches(name, keep)) {
            		        allowedFormatCache.add(name);
            		        return true;
            			}
            		}
            	}
                blockedFormatCache.add(name);
                return false;
            }
        }

        allowedFormatCache.add(name);
        return true;
    }
    
    public Set<String> getAlts() {
        return Collections.unmodifiableSet(altDefs.keySet());
    }
    
    public VCFAltDef getAlt(String id) {
        return altDefs.get(id);
    }
    
    public Set<String> getContigNames() {
        return Collections.unmodifiableSet(contigDefs.keySet());
//        List<String> names = new ArrayList<String>();
//        for (String line: lines) {
//            if (line.startsWith("##contig=<") && line.endsWith(">")) {
//                // contig lines are formatted:
//                // ##contig=<ID=name,length=num,...>
//                
//                for (String s: line.substring(10, line.length()-1).split(",")) {
//                    String[] spl = s.split("=");
//                    if (spl[0].toUpperCase().equals("ID")) {
//                        names.add(spl[1]);
//                    }
//                }
//            }
//        }
//        
//        return names;
    }

    public long getContigLength(String id) {
        if (!contigDefs.containsKey(id)) {
            return -1;
        }
        return contigDefs.get(id).getLength();
        
//        for (String line: lines) {
//            if (line.startsWith("##contig=<") && line.endsWith(">")) {
//                // contig lines are formatted:
//                // ##contig=<ID=name,length=num,...>
//                
//                boolean found = false;
//                int length = -1;
//                for (String s: line.substring(10, line.length()-1).split(",")) {
//                    String[] spl = s.split("=");
//                    if (spl[0].toUpperCase().equals("ID")) {
//                        if (spl[1].equals(name)) {
//                            found = true;
//                        }
//                    }
//                    if (spl[0].toUpperCase().equals("LENGTH")) {
//                        length = Integer.parseInt(spl[1]);
//                    }
//                }
//                if (found) { 
//                    return length;
//                }
//            }
//        }
//        return -1;
    }

	public static String quoteString(String s) {
		s=s.replaceAll("\\\\", "\\\\\\\\");
		s=s.replaceAll("\\\"", "\\\\\"");
		return s;
	}
	public static Map<String, String> parseQuotedLine(String s) throws VCFParseException {
		Map<String, String> values = new HashMap<String, String>();
		
		String k = null;
		String acc = "";
		
		boolean inquote = false;
		
		for (int i=0; i<s.length(); i++) {
			if (k == null) {
				if (s.charAt(i) == '=') {
					k = acc;
					inquote = false;
					acc = "";
				} else {
					acc += s.charAt(i);
				}
			} else {
				if (!inquote && s.charAt(i) == '"') {
					inquote = true;
				} else if (inquote && s.charAt(i) == '"') {
					inquote = false;
				} else if (s.charAt(i) == ',') {
					values.put(k, acc);
					
					k = null;
					acc = "";		
				} else if (inquote && s.charAt(i) == '\\' && i < s.length()-1) {
					// escape the next char...
					acc += s.charAt(i+1);
					i++;
				} else {
					acc += s.charAt(i);
				}
			}
		}
		
		if (k != null) {
			values.put(k, acc);
		}
		
		return values;
	}
}
