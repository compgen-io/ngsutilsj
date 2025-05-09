package io.compgen.ngsutils.vcf.filter;

import java.util.HashSet;
import java.util.Set;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.vcf.VCFRecord;

public class ChromPassFilter extends VCFAbstractFilter {
	protected Set<String> chroms = new HashSet<String>();

	
	public ChromPassFilter(String chrom) {
		super(generateID(chrom), generateDescription(chrom));
		this.chroms.add(chrom);
	}

	public ChromPassFilter(String[] chroms) {
		super(generateID(StringUtils.join("_", chroms)), generateDescription(StringUtils.join(",", chroms)));
		for (String chrom: chroms) {
			this.chroms.add(chrom);
		}
	}

	private static String generateDescription(String chrom) {
        return "Chromosome is not "+chrom;
	}

	private static String generateID(String chrom) {
		return "CHROM_PASS_"+chrom;
	}

	@Override
	protected boolean innerFilter(VCFRecord record) throws VCFFilterException {
		if (chroms.contains(record.getChrom())) {
			return false;
		}
	    
	    return true;
	}
}
