package io.compgen.ngsutils.vcf.export;

import java.util.List;

import io.compgen.common.ListBuilder;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class ExportInfoField implements VCFExport {
	private final String key;
	protected VCFHeader header = null;
	private String alleleName;
	protected boolean ignoreMissing = false;
	protected String missingValue = ".";
		
	public ExportInfoField(String key, String alleleName, boolean ignoreMissing) {
		this.key = key;
		this.alleleName = alleleName;
		this.ignoreMissing = ignoreMissing;
	}

	public ExportInfoField(String key, String alleleName) {
		this(key, alleleName, false);
	}


	public void setHeader(VCFHeader header) {
		this.header = header;
	}

	public void setMissingValue(String missingValue) {
		this.missingValue = missingValue;
	}

	@Override
	public List<String> getFieldNames() {
		return new ListBuilder<String>().add(key).list();
	}

	@Override
	public void export(VCFRecord rec, List<String> outs) throws VCFExportException {
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
