package io.compgen.ngsutils.vcf;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class VCFFilterDef {
	final public String id;
	final public String description;
	final public Map<String, String> extras;
	final public String origLine;
	
	private VCFFilterDef(String id, String description, Map<String, String> extras, String origLine) {
		this.id = id;
		this.description = description;
		if (extras != null && extras.size()>0) {
			Map<String, String> tmp = new HashMap<String, String>();
			tmp.putAll(extras);
			this.extras = Collections.unmodifiableMap(tmp);
		} else {
			this.extras = null;
		}
		this.origLine = origLine;
	}
	
	public String toString() {
		if (origLine != null) {
			return origLine;
		}
		String s = "##FILTER=<";
		s += "ID="+id;
		s += ",Description=\""+VCFHeader.quoteString(description)+"\"";
		if (extras != null) {
			for (Entry<String, String> kv : extras.entrySet()) {
				s+=","+kv.getKey()+"=\""+VCFHeader.quoteString(kv.getValue())+"\"";
			}
		}
		return s+">";
	}
	
	public static VCFFilterDef parse(String line) throws VCFParseException {
		if (line.startsWith("##FILTER=<") && line.endsWith(">")) {
			Map<String, String> vals = VCFHeader.parseQuotedLine(line.substring(10, line.length()-1));
			String id = vals.remove("ID");
			String desc = vals.remove("Description");
			
			return build(id, desc, vals, line);
			
		}
		throw new VCFParseException("Can't parse the line: "+ line);
	}
	
	public static VCFFilterDef build(String id, String description) {
		return new VCFFilterDef(id, description, null, null);
	}

	public static VCFFilterDef build(String id, String description, Map<String, String> extras, String origLine) {
		return new VCFFilterDef(id, description, extras, origLine);
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
		VCFFilterDef other = (VCFFilterDef) obj;
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
