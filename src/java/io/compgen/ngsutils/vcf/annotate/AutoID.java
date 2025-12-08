package io.compgen.ngsutils.vcf.annotate;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFRecord;

public class AutoID extends AbstractBasicAnnotator {
	
	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		String[] idValues= new String[record.getAlt().size()];
		for (int i=0; i<record.getAlt().size(); i++) {
			idValues[i] = StringUtils.join("_", new String[] {
					record.getChrom(),
					""+record.getPos(),
					record.getRef(),
					record.getAlt().get(i)
					
			});
		}
		record.setDbSNPID(StringUtils.join(";", idValues));
	}

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		// NO-OP
		
	}
}