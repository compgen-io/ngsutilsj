package io.compgen.ngsutils.vcf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.GlobUtils;

public class VCFAttributes {
	private Map<String, VCFAttributeValue> attributes = new LinkedHashMap<String, VCFAttributeValue>();
	
	public void put(String key, VCFAttributeValue value) throws VCFAttributeException {
		if (value == null || (value.equals(VCFAttributeValue.EMPTY.value) && value != VCFAttributeValue.EMPTY)) {
			throw new VCFAttributeException("You cannot set an empty VCF attribute (INFO/FORMAT) value ("+key+")");
		}
		attributes.put(key, value);
	}
	
    public VCFAttributeValue get(String key) {
        return attributes.get(key);
    }

    public List<String> findKeys(String keyGlob) {
        List<String> keys = new ArrayList<String>();

        for (String k: attributes.keySet()) {
            if (GlobUtils.matches(k,  keyGlob)) {
                keys.add(k);
            }
        }
        
        return keys;
    }

    
    public boolean contains(String key) {
		return attributes.containsKey(key);
	}
	
	public List<VCFAttributeValue> get(List<String> keys) {
		List<VCFAttributeValue> vals = new ArrayList<VCFAttributeValue>();
		for (String key: keys) {
			if (!contains(key)) {
				vals.add(VCFAttributeValue.MISSING);
			} else {
				vals.add(get(key));
			}
		}
		return vals;
	}
	
	public VCFAttributeValue remove(String key) {
		return attributes.remove(key);
	}
	
	public List<String> getKeys() {
		return new ArrayList<String>(attributes.keySet());
	}

	public static VCFAttributes parseInfo(String s, VCFHeader header) throws VCFParseException, VCFAttributeException {
		VCFAttributes attrs = new VCFAttributes();
		
        if (!s.equals(VCFAttributeValue.MISSING.toString())) {
    		for (String el: s.split(";")) {
    			if (el.indexOf("=") == -1) {
                    if (header == null || header.isInfoAllowed(el)) {
                        attrs.put(el, VCFAttributeValue.EMPTY);
                    }
    			} else {
    			    try {
    			        String[] kv = el.split("=", 2);				
                        if (header == null || header.isInfoAllowed(kv[0])) {
                            attrs.put(kv[0], VCFAttributeValue.parse(kv[1]));
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                     	throw new VCFParseException(e);
    			    }
    			}
		    }
		}
		return attrs;
	}
	
	public static VCFAttributes parseFormat(String s, List<String> formatKeys, VCFHeader header) throws VCFParseException, VCFAttributeException {
		VCFAttributes attrs = new VCFAttributes();
		
		String[] spl = s.split(":");

//      Trailing fields can be skipped, so just split and pull key from {format}		
//		if (format.size() != spl.length) {
//		throw new VCFParseException("Unable to parse genotype field: "+s);
//		}

		// if GT is present in {format}, it must be in the input string for each sample.
		if (formatKeys != null && formatKeys.size() > 0 && formatKeys.get(0).equals("GT") && spl.length<1) {
			throw new VCFParseException("Unable to parse genotype field: "+s);
		}
		
		for (int i=0; i<formatKeys.size(); i++) {
		    if (header == null || header.isFormatAllowed(formatKeys.get(i))) {
		    	if (spl.length > i) {
		    		attrs.put(formatKeys.get(i), VCFAttributeValue.parse(spl[i]));
		    	} else {
		    		attrs.put(formatKeys.get(i), VCFAttributeValue.MISSING);
		    	}
		    }
		}
		return attrs;
	}

	// output in INFO format
	public String toString() {
		List<String> outcols = new ArrayList<String>();
		for (Entry<String, VCFAttributeValue> kv: attributes.entrySet()) {
			if (kv.getValue()==VCFAttributeValue.EMPTY) {
				outcols.add(kv.getKey());
			} else {
				outcols.add(kv.getKey()+"="+kv.getValue().toString());
			}
		}
		if (outcols.size()>0) {
		    return StringUtils.join(";", outcols);
		} else {
		    return VCFRecord.MISSING;
		}
	}

	// output in GENOTYPE format (with given FORMAT keys)
	public String toString(List<String> format) {
		List<VCFAttributeValue> vals = get(format);
		
		// GT can't be trimmed if it is present
		int limit = 0;
		if (format.size()>0 && format.get(0).equals("GT")) {
			limit = 1;
		}
		
		// FORMAT values can be trimmed from the end if they are missing
		while (vals.size() > limit && vals.get(vals.size()-1) == VCFAttributeValue.MISSING) {
			vals.remove(vals.size()-1);
		}
		
		List<String> outcols = new ArrayList<String>();
		for (VCFAttributeValue val: vals) {
			outcols.add(val.toString());
		}
		
		return StringUtils.join(":", outcols);
	}

	public void putFlag(String name) {
		try {
			put(name, VCFAttributeValue.EMPTY);
		} catch (VCFAttributeException e) {
			// the exception looks for this specific case, so it will never be thrown
		}
	}

}