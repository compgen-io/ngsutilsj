package io.compgen.ngsutils.vcf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.compgen.common.ListBuilder;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.GlobUtils;

public class VCFHeader {
	protected String fileformat;
	protected Map<String,VCFAnnotationDef> infoDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFAnnotationDef> formatDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFFilterDef> filterDefs = new LinkedHashMap<String, VCFFilterDef>();
	protected List<String> lines = new ArrayList<String>();

	protected Map<String, VCFContigDef> contigDefs = new LinkedHashMap<String, VCFContigDef>();
	
	protected String headerLine;
	
	protected String[] samples = null;
	
    private Set<String> removeFilter = null;
    private Set<String> removeInfo = null;
    private Set<String> removeFormat = null;
//    private Set<String> removeSample = null;

    private Set<String> allowedFilterCache = new HashSet<String>();
    private Set<String> blockedFilterCache = new HashSet<String>();
    private Set<String> allowedInfoCache = new HashSet<String>();
    private Set<String> blockedInfoCache = new HashSet<String>();
    private Set<String> allowedFormatCache = new HashSet<String>();
    private Set<String> blockedFormatCache = new HashSet<String>();
    
	public VCFHeader(String fileformat, List<String> input, String headerLine, Set<String> removeFilter, Set<String> removeInfo, Set<String> removeFormat, Set<String> removeSample) throws VCFParseException {
		if (fileformat == null) {
			throw new VCFParseException("Missing format in header?");
		}

		this.fileformat = fileformat;
		this.headerLine = headerLine;
		
        this.removeFilter = removeFilter;
        this.removeInfo = removeInfo;
        this.removeFormat = removeFormat;
//        this.removeSample = removeSample;
		
		for (String line: input) {
			if (line.startsWith("##INFO=")) {
			    VCFAnnotationDef info = VCFAnnotationDef.parseString(line);
                boolean match = false;
                if (removeInfo != null) {
                    for (String remove: removeInfo) {
                        if (GlobUtils.matches(info.id, remove)) {
                            match = true;
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
                        }
                    }
                }
                if (!match) {
                	lines.add(line);
                }
            } else if (line.startsWith("##contig=")) {
                VCFContigDef contig = VCFContigDef.parse(line);
                addContig(contig);
			} else {
				lines.add(line);
			}
		}
		
		String[] spl = headerLine.split("\t");
		
		if (spl.length > 9) {
			
			List<String> sampleList = new ArrayList<String>();
			for (int i=9; i< spl.length; i++) {
                boolean match = false;
                if (removeSample != null) {
                    for (String remove: removeSample) {
                        if (GlobUtils.matches(spl[i], remove)) {
                            match = true;
                        }
                    }
                }
                if (!match) {
                	sampleList.add(spl[i]);
//    				samples[i-9]=spl[i];
                }
			}
			
			samples = sampleList.toArray(new String[] {});
		}
	}

	public List<String> getSamples() {
		if (samples != null) {
			return ListBuilder.build(samples);
		}
		return new ArrayList<String>();
	}
	
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
        outlines.addAll(lines);

		for (String line: outlines) {
			StringUtils.writeOutputStream(out, line + "\n");
		}

		if (includeAll) {
			String header = "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT";
			
			if (samples != null) {
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
	    
        // You can ID a sample by index (1, 2, 3)
	    try {
	        int i = Integer.parseInt(name);
	        return i - 1;
	        
	    } catch (NumberFormatException e) {
	        // ignore this...
	    }
	    
        // OR by name NORMAL, TUMOR, etc...
	    
		for (int i=0; i<samples.length; i++) {
			if (samples[i].equals(name)) {
				return i;
			}
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
                blockedFormatCache.add(name);
                return false;
            }
        }

        allowedFormatCache.add(name);
        return true;
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
