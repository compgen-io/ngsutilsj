package io.compgen.ngsutils.vcf;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.compgen.common.ListBuilder;

public class VCFAnnotationDef {
	final public boolean isInfo;
	final public String id;
	final public String number;
	final public String type;
	final public String description;
	final public String source;
	final public String version;
	final public Map<String, String> extras;
	final public String origLine;

	final public static List<String> VALID_INFO_TYPE = ListBuilder.build(new String[]{"Integer", "Float", "Flag", "Character", "String"});
	final public static List<String> VALID_FORMAT_TYPE = ListBuilder.build(new String[]{"Integer", "Float", "Character", "String"});
	
	private VCFAnnotationDef(boolean isInfo, String id, String number, String type, String description, String source,
			String version, Map<String, String> extras, String origLine) throws VCFParseException {
		this.isInfo = isInfo;
		this.id = id;
		this.number = number;
		this.type = type;
		this.description = description;
		this.source = source;
		this.version = version;
		if (extras != null && extras.size()>0) {
			Map<String, String> tmp = new HashMap<String, String>();
			tmp.putAll(extras);
			this.extras = Collections.unmodifiableMap(tmp);
		} else {
			this.extras = null;
		}
		this.origLine = origLine;
		
		if (id == null || type == null || number == null || description == null) {
			throw new VCFParseException("Invalid value(s) for INFO/FORMAT line!");
		}		
		
		if (isInfo) {
			if (!VALID_INFO_TYPE.contains(type)) {
				throw new VCFParseException("Invalid \"Type\" value for INFO line! ("+type+")");
			}
		} else {
			if (!VALID_FORMAT_TYPE.contains(type)) {
				throw new VCFParseException("Invalid \"Type\" value for FORMAT line! ("+type+")");
			}
		}
		
		if (!number.equals("A") && !number.equals("R") && !number.equals("G") && !number.equals(".")) {
			try {
				@SuppressWarnings("unused")
				int num = Integer.parseInt(number);
			} catch (NumberFormatException e) {
				throw new VCFParseException(e);
			}
		}
		
	}
	
	

	private VCFAnnotationDef(boolean isInfo, String id, String number, String type, String description, String source,
			String version, Map<String, String> extras, String origLine, boolean isClone) {
		this.isInfo = isInfo;
		this.id = id;
		this.number = number;
		this.type = type;
		this.description = description;
		this.source = source;
		this.version = version;
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
		String s = "";
		if (isInfo) {
			s += "##INFO=<";
		} else {
			s += "##FORMAT=<";
		}
		s += "ID="+id;
		s += ",Number="+number;
		s += ",Type="+type;
		s += ",Description=\""+VCFHeader.quoteString(description)+"\"";
		
		if (source != null) {
			s+=",Source=\""+VCFHeader.quoteString(source)+"\"";
		}
		if (version != null) {
			s+=",Version=\""+VCFHeader.quoteString(version)+"\"";
		}
		if (extras != null) {
			for (Entry<String, String> kv : extras.entrySet()) {
				s+=","+kv.getKey()+"=\""+VCFHeader.quoteString(kv.getValue())+"\"";
			}
		}

		return s+">";
	}

	public static VCFAnnotationDef parseString(String line) throws VCFParseException {
		try {
			boolean isInfo = false;
			Map<String, String> vals;
			if (line.startsWith("##INFO=<") && line.endsWith(">")) {
				isInfo = true;
				vals = VCFHeader.parseQuotedLine(line.substring(8, line.length()-1));
			} else if (line.startsWith("##FORMAT=<") && line.endsWith(">")) {
				vals = VCFHeader.parseQuotedLine(line.substring(10, line.length()-1));
			} else {
				throw new VCFParseException("Can't parse the line: "+ line);
			}
			
			String id = vals.remove("ID");
			String type = vals.remove("Type");
			String number = vals.remove("Number");
			String desc = vals.remove("Description");
			String source = vals.remove("Source");
			String version = vals.remove("Version");
			
			if (isInfo) {
				return info(id, number, type, desc, source, version, vals, line);
			}
			return format(id, number, type, desc, source, version, vals, line);
			
		} catch (VCFParseException e) {
			throw new VCFParseException("Can't parse the line: "+ line, e);
		}
	}

	public static VCFAnnotationDef info(String id, String number, String type, String description, String source, String version, Map<String, String> extras, String line) throws VCFParseException {
		return new VCFAnnotationDef(true, id, number, type, description, source, version, extras, line);
	}
	public static VCFAnnotationDef info(String id, String number, String type, String description) throws VCFParseException {
		return info(id, number, type, description, null, null, null, null);
	}
	public static VCFAnnotationDef format(String id, String number, String type, String description, String source, String version, Map<String, String> extras, String line) throws VCFParseException {
		return new VCFAnnotationDef(false, id, number, type, description, source, version, extras, line);
	}
	public static VCFAnnotationDef format(String id, String number, String type, String description) throws VCFParseException {
		return format(id, number, type, description, null, null, null, null);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + (isInfo ? 1231 : 1237);
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
		VCFAnnotationDef other = (VCFAnnotationDef) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (isInfo != other.isInfo) {
			return false;
		}
		return true;
	}

	public VCFAnnotationDef copy(String newId) throws VCFParseException {
		return new VCFAnnotationDef(isInfo, newId, number, type, description, source, version, extras, null);
	}

	public VCFAnnotationDef clone() {
		return new VCFAnnotationDef(isInfo, id, number, type, description, source, version, extras, null, false);
	}

}
