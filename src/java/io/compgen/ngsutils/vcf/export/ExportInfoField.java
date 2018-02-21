package io.compgen.ngsutils.vcf.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.compgen.ngsutils.support.GlobUtils;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class ExportInfoField implements VCFExport {
	private final String keyName;
	protected VCFHeader header = null;
	private String alleleName;
	protected boolean ignoreMissing = false;
	protected String missingValue = ".";
	
	protected List<String> ids = null;
		
	public ExportInfoField(String keyName, String alleleName, boolean ignoreMissing) {
		this.keyName = keyName;
		this.alleleName = alleleName;
		this.ignoreMissing = ignoreMissing;
	}

	public ExportInfoField(String key, String alleleName) {
		this(key, alleleName, false);
	}


	public void setHeader(VCFHeader header) {
		this.header = header;
		List<String> ids = new ArrayList<String>();
		for (String id: header.getInfoIDs()) {
		    if (GlobUtils.matches(id, keyName)) {
		        ids.add(id);
		    }
		}		
        this.ids = Collections.unmodifiableList(ids);
	}

	public void setMissingValue(String missingValue) {
		this.missingValue = missingValue;
	}

	@Override
	public List<String> getFieldNames() {
		return ids;
	}

	@Override
	public void export(VCFRecord rec, List<String> outs) throws VCFExportException {
	    for (String key: ids) {
    		VCFAttributeValue val = rec.getInfo().get(key);
    		if (val == null) {
    			if (ignoreMissing) {
    				outs.add("");
    			} else {
    				throw new VCFExportException("Unable to find INFO field: "+key);
    			}
    		} else if (ignoreMissing && val == VCFAttributeValue.EMPTY) {
    			// if the INFO field is a flag, this will add the flag instead of the missing value...
    			outs.add(key);
    		} else {
    			try {
    				if (alleleName!=null && alleleName.equals("sum")) {
    					String t = "" + val.asDouble(alleleName);
    					if (t.endsWith(".0")) {
    						outs.add(t.substring(0,  t.length()-2));
    					} else {
    						outs.add(t);
    					}
    				} else if (alleleName!=null && alleleName.equals("min")) {
    					double minVal = Double.NaN;
    					for (String v: val.asString(null).split(",")) {
    						double d = Double.parseDouble(v);
    						if (Double.isNaN(minVal) || d < minVal) {
    							minVal = d;
    						}
    					}
    					if (!Double.isNaN(minVal)) {
    						String t = "" + minVal;
    						if (t.endsWith(".0")) {
    							outs.add(t.substring(0,  t.length()-2));
    						} else {
    							outs.add(t);
    						}
    					}
    				} else if (alleleName!=null && alleleName.equals("max")) {
    					double maxVal = Double.NaN;
    					for (String v: val.asString(null).split(",")) {
    						double d = Double.parseDouble(v);
    						if (Double.isNaN(maxVal) || d > maxVal) {
    							maxVal = d;
    						}
    					}
    					if (!Double.isNaN(maxVal)) {
    						String t = "" + maxVal;
    						if (t.endsWith(".0")) {
    							outs.add(t.substring(0,  t.length()-2));
    						} else {
    							outs.add(t);
    						}
    					}
    				} else {
    					if (val == VCFAttributeValue.MISSING) {
    						outs.add(missingValue);
    					} else {
    						outs.add(val.asString(alleleName));
    					}
    				}
    			} catch (VCFAttributeException e) {
    				throw new VCFExportException(e);
    			}
    		}
		}
	}
}
