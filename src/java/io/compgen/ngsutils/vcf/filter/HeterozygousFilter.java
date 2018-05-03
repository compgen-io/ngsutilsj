package io.compgen.ngsutils.vcf.filter;

import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class HeterozygousFilter extends VCFAbstractFilter {
	
	public HeterozygousFilter() {
		super("heterozygous", "Heterzygous variant (in any sample)");
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
        for (int i=0; i<record.getSampleAttributes().size(); i++) {
            String val;
            try {
                val = record.getSampleAttributes().get(i).get("GT").asString(null);
            } catch (VCFAttributeException e) {
                throw new VCFFilterException(e);
            }

            // if 0/0 or 1/1, etc -- skip
            if (val.indexOf('/')>-1) {
                String[] v2 = val.split("/");
                if (v2.length == 2 && !v2[0].equals(v2[1])) {
                    return true;
                }
            } else if (val.indexOf('|')>-1) {
                String[] v2 = val.split("\\|");
                if (v2.length == 2 && !v2[0].equals(v2[1])) {
                    return true;
                }
            }
        }
        return false;
	}
}
