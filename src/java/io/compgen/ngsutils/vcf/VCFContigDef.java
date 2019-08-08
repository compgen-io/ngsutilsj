package io.compgen.ngsutils.vcf;

import java.util.Map;

public class VCFContigDef {
	final public String id;
	final public long length;
	final public String origLine;
	
	private VCFContigDef(String id, long length, String origLine) {
		this.id = id;
		this.length = length;
		this.origLine = origLine;
	}
	
	public String toString() {
		if (origLine != null) {
			return origLine;
		}
		String s = "##contig=<";
		s += "ID="+id;
		s += ",length="+length+">";
		return s;
	}
	
	public static VCFContigDef parse(String line) throws VCFParseException {
		if (line.startsWith("##contig=<") && line.endsWith(">")) {
			Map<String, String> vals = VCFHeader.parseQuotedLine(line.substring(10, line.length()-1));
			String id = vals.remove("ID");
			String len = vals.remove("length");
			
			return build(id, Long.parseLong(len), line);
			
		}
		throw new VCFParseException("Can't parse the line: "+ line);
	}
	
    public static VCFContigDef build(String id, long length) {
        return new VCFContigDef(id, length, null);
    }

    public static VCFContigDef build(String id, long length, String orig) {
        return new VCFContigDef(id, length, orig);
    }

    public long getLength() {
        return length;
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
		VCFContigDef other = (VCFContigDef) obj;
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
