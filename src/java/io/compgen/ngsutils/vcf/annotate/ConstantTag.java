package io.compgen.ngsutils.vcf.annotate;

import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class ConstantTag extends AbstractBasicAnnotator {
	protected final String key;
	protected final String value;
	
	public ConstantTag(String key) {
		this(key, null);
	}
	public ConstantTag(String key, String value) {
		this.key = key;
		this.value = value;				
	}
	
	
	public VCFAnnotationDef[] getAnnotationTypes() throws VCFAnnotatorException {
		try {
			if (this.value == null) {
				return new VCFAnnotationDef[] {
						VCFAnnotationDef.info(key, "0", "Flag", key),
				};
			} else {
				return new VCFAnnotationDef[] {
						VCFAnnotationDef.info(key, ".", "String", key),
				};
			}
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		for (VCFAnnotationDef def : getAnnotationTypes()) {
			header.addFormat(def);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		try {
			if (value == null) {
				record.getInfo().putFlag(key);
			} else {
				record.getInfo().put(key, new VCFAttributeValue(value));
			}
		} catch (VCFAttributeException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}