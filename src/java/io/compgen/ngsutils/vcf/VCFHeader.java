package io.compgen.ngsutils.vcf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.compgen.common.ListBuilder;
import io.compgen.common.StringUtils;

public class VCFHeader {
	protected String format;
	protected Map<String,VCFAnnotationDef> infoDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFAnnotationDef> formatDefs = new LinkedHashMap<String, VCFAnnotationDef>();
	protected Map<String,VCFFilterDef> filterDefs = new LinkedHashMap<String, VCFFilterDef>();
	protected List<String> lines = new ArrayList<String>();

	protected String headerLine;
	
	protected String[] samples = null;
	
	public VCFHeader(String format, List<String> input, String headerLine) throws VCFParseException {
		if (format == null) {
			throw new VCFParseException("Missing format in header?");
		}
		
		this.format = format;
		this.headerLine = headerLine;
		
		for (String line: input) {
			if (line.startsWith("##INFO=")) {
				addInfo(VCFAnnotationDef.parseString(line));
			} else if (line.startsWith("##FORMAT=")) {
				addFormat(VCFAnnotationDef.parseString(line));
			} else if (line.startsWith("##FILTER=")) {
				addFilter(VCFFilterDef.parse(line));
			} else {
				lines.add(line);
			}
		}
		
		String[] spl = headerLine.split("\t");
		
		if (spl.length > 9) {
			samples = new String[spl.length-9];
			for (int i=9; i< spl.length; i++) {
				samples[i-9]=spl[i];
			}
		}
	}

	public List<String> getSamples() {
	    return ListBuilder.build(samples);
	}
	
	public void addLine(String line) {
		this.lines.add(line);
	}
	public void write(OutputStream out) throws IOException {
		write(out, true, false);
		
	}
	
	public void write(OutputStream out, boolean includeAll, boolean strip) throws IOException {

		if (includeAll) {
			while (!format.startsWith("##")) {
				format = "#" + format; 
			}
			StringUtils.writeOutputStream(out, format + "\n");
		}
		List<String> outlines = new ArrayList<String>();;
		
		if (!strip) {
    		for (VCFAnnotationDef def: infoDefs.values()) {
    			outlines.add(def.toString());
    		}
    		for (VCFFilterDef def: filterDefs.values()) {
    			outlines.add(def.toString());
    		}
    		for (VCFAnnotationDef def: formatDefs.values()) {
    			outlines.add(def.toString());
    		}
            outlines.addAll(lines);
		} else {
		    for (String line: lines) {
		        // when stripping the output, still output all other lines except the sample lines
		        if (!line.startsWith("##SAMPLE=")) {
		            outlines.add(line);
		        }
		    }
		}
		
		
		for (String line: outlines) {
			StringUtils.writeOutputStream(out, line + "\n");
		}

		if (includeAll) {
		    if (strip) {
                StringUtils.writeOutputStream(out, "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
		    } else {
    			if (!headerLine.startsWith("#")) {
    				headerLine = "#" + headerLine; 
    			}
    			StringUtils.writeOutputStream(out, headerLine + "\n");
            }
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
	
	public int getSamplePosByName(String name) {
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
	
	public VCFAnnotationDef getFormatDef(String id) {
		return formatDefs.get(id);
	}

    public Set<String> getFormatIDs() {
        return formatDefs.keySet();
    }

    public VCFAnnotationDef getInfoDef(String id) {
        return infoDefs.get(id);
    }

    public Set<String> getInfoIDs() {
        return infoDefs.keySet();
    }

    public List<String> getContigNames() {
        List<String> names = new ArrayList<String>();
        for (String line: lines) {
            if (line.startsWith("##contig=<") && line.endsWith(">")) {
                // contig lines are formatted:
                // ##contig=<ID=name,length=num,...>
                
                for (String s: line.substring(10, line.length()-1).split(",")) {
                    String[] spl = s.split("=");
                    if (spl[0].toUpperCase().equals("ID")) {
                        names.add(spl[1]);
                    }
                }
            }
        }
        
        return names;
    }

    public int getContigLength(String name) {
        for (String line: lines) {
            if (line.startsWith("##contig=<") && line.endsWith(">")) {
                // contig lines are formatted:
                // ##contig=<ID=name,length=num,...>
                
                boolean found = false;
                int length = -1;
                for (String s: line.substring(10, line.length()-1).split(",")) {
                    String[] spl = s.split("=");
                    if (spl[0].toUpperCase().equals("ID")) {
                        if (spl[1].equals(name)) {
                            found = true;
                        }
                    }
                    if (spl[0].toUpperCase().equals("LENGTH")) {
                        length = Integer.parseInt(spl[1]);
                    }
                }
                if (found) { 
                    return length;
                }
            }
        }
        return -1;
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
