package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GenicRegion;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class GTFGene extends AbstractBasicAnnotator {
	final protected String filename;
	protected GTFAnnotationSource gtf;
	protected List<String> requiredTags = null;
	
	public GTFGene(String filename) throws IOException {
		this.filename = filename;
	}
	
	public void addRequiredTags(List<String> tags) {
		if (requiredTags == null) {
			requiredTags = new ArrayList<String>();
		}
		for (String t : tags) {
			requiredTags.add(t);
		}
	}

	
	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
			/*
			 * TODO: add more? From VEP docs:
			 * GTF format expectations

				The following GTF entity types will be parsed by VEP:
				
				cds (or CDS)
				stop_codon
				exon
				gene
				transcript
				Entities are linked by an attribute named for the parent entity type e.g. exon is linked to transcript by transcript_id, transcript is linked to gene by gene_id.
				
				Transcript biotypes are defined in attributes named "biotype", "transcript_biotype" or "transcript_type". If none of these exist, VEP will attempt to interpret the source field (2nd column) of the GTF as the biotype.
				

				Codon position -- 1, 2, 3 -- might be good!
				AA substitution...

			 */
			
			header.addInfo(VCFAnnotationDef.info("CG_GENE", "1", "String", "Gene name", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info("CG_GENE_STRAND", "1", "String", "Gene strand", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info("CG_GENE_REGION", "1", "String", "Gene region", filename, null, null, null));
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		if (this.gtf == null) {
			try {
				this.gtf = new GTFAnnotationSource(filename, requiredTags);
			} catch (NumberFormatException | IOException e) {
				throw new VCFAnnotatorException(e);
			}
		}
        GenomeSpan pos;
        try {
            pos = new GenomeSpan(getChrom(record), getPos(record));
        } catch (VCFAnnotatorMissingAltException e) {
            return;
        }
		
		List<String> geneNames = new ArrayList<String>();
		List<String> strands = new ArrayList<String>();

		for (io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene gene : gtf.findAnnotation(pos)) {
			geneNames.add(gene.getGeneName());
			strands.add(gene.getStrand().toString());
		}
		
		try {
			if (geneNames.size() == 0) {
				record.getInfo().put("CG_GENE", VCFAttributeValue.MISSING);
				record.getInfo().put("CG_GENE_REGION", VCFAttributeValue.MISSING);
				record.getInfo().put("CG_GENE_STRAND", VCFAttributeValue.MISSING);
			} else {
				GenicRegion region = gtf.findGenicRegionForPos(pos);
				String geneRegionName = ".";
				if (region != null) {
					geneRegionName = region.name;
				}
				
				record.getInfo().put("CG_GENE", new VCFAttributeValue(StringUtils.join(",", geneNames)));
				record.getInfo().put("CG_GENE_REGION", new VCFAttributeValue(geneRegionName));
				record.getInfo().put("CG_GENE_STRAND", new VCFAttributeValue(StringUtils.join(",", strands)));
			}
		} catch (VCFAttributeException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}