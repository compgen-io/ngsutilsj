package io.compgen.ngsutils.vcf;

import java.util.Map;

public class VCFAltDef {
	final public String id;
	final public String desc;
	final public String origLine;
	
	private VCFAltDef(String id, String desc, String origLine) {
		this.id = id;
		this.desc = desc;
		this.origLine = origLine;
	}
	
	public VCFAltDef(String id, String desc) {
		this(id, desc, null);
	}

	public String toString() {
		if (origLine != null) {
			return origLine;
		}
		String s = "##ALT=<";
		s += "ID="+id;
		if (desc != null && !desc.equals("")) {
			s += ",Description=\""+desc+"\"";
		}
		s += ">";
		return s;
	}
	
	public static VCFAltDef parse(String line) throws VCFParseException {
		if (line.startsWith("##ALT=<") && line.endsWith(">")) {
			Map<String, String> vals = VCFHeader.parseQuotedLine(line.substring(7, line.length()-1));
			String id = vals.remove("ID");
			String desc = vals.remove("Description");
			
			return new VCFAltDef(id, desc, line);
			
		}
		throw new VCFParseException("Can't parse the line: "+ line);
	}
	

    public String getDescription() {
        return desc;
    }
    
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		VCFAltDef other = (VCFAltDef) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}
}
