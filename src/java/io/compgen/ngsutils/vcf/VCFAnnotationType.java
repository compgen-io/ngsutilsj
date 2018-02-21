package io.compgen.ngsutils.vcf;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public class VCFAnnotationType {
	public static final VCFAnnotationType REF = new VCFAnnotationType("R");
	public static final VCFAnnotationType ALT = new VCFAnnotationType("A");
	public static final VCFAnnotationType GEN = new VCFAnnotationType("G");
	public static final VCFAnnotationType DOT = new VCFAnnotationType(".");
	
	private static final Map<String, VCFAnnotationType> cache = new HashMap<String, VCFAnnotationType>();
	
	static {
		cache.put("R", REF);
		cache.put("A", ALT);
		cache.put("G", GEN);
		cache.put(".", DOT);
	}
	
	private String val;
	
	private VCFAnnotationType(String val) {
		this.val = val;
	}
	
	public String toString() {
		return val;
	}
	
	public static VCFAnnotationType get(String val) {
		if (!cache.containsKey(val)) {
			cache.put(val,  new VCFAnnotationType(val));
		}
		return cache.get(val);
	}
}
