package io.compgen.ngsutils.vcf.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.compgen.ngsutils.support.GlobUtils;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class ExportFormatField implements VCFExport {
	protected final String keyName;
	protected final String sample;
	protected final String alleleName;
	protected VCFHeader header = null;
	private int sampleIdx = -1;
	protected boolean ignoreMissing;
	protected String missingValue=".";
	
    protected List<String> ids = null;
	
	public ExportFormatField(String keyName, String sample, String alleleName, boolean ignoreMissing) {
		this.keyName = keyName;
		this.sample = sample;
		this.alleleName = alleleName;
		this.ignoreMissing = ignoreMissing;
	}

	public void setHeader(VCFHeader header) {
		this.header = header;
		if (sample != null) {
			sampleIdx = header.getSamplePosByName(sample);
		}
	    List<String> ids = new ArrayList<String>();
        for (String id: header.getFormatIDs()) {
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
		if (sampleIdx == -1) {
			for (VCFAttributes attr: rec.getSampleAttributes()) {
				exportVal(attr, outs);
			}
		} else {
			exportVal(rec.getSampleAttributes().get(sampleIdx), outs);
		}
	}

	private void exportVal(VCFAttributes attr, List<String> outs) throws VCFExportException {
	    for (String key: ids) {
    		VCFAttributeValue val = attr.get(key);
    		if (val == null) {
    			if (ignoreMissing) {
    				outs.add("");
    			} else {
    				throw new VCFExportException("Unable to find FORMAT field: "+key);
    			}
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
