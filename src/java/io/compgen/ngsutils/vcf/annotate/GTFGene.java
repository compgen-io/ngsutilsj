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
	protected String prefix = null;
	
	public GTFGene(String filename) throws IOException {
		this.filename = filename;
		this.prefix = "CG_";
	}
	public GTFGene(String filename, String prefix) throws IOException {
		this.filename = filename;
		this.prefix = prefix;
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
			
			header.addInfo(VCFAnnotationDef.info(prefix+"GENE", "1", "String", "Gene name", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info(prefix+"GENEID", "1", "String", "Gene ID", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info(prefix+"BIOTYPE", "1", "String", "Gene biotype (if available)", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info(prefix+"STRAND", "1", "String", "Gene strand", filename, null, null, null));
			header.addInfo(VCFAnnotationDef.info(prefix+"REGION", "1", "String", "Gene region", filename, null, null, null));
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
		
		List<String> geneIds = new ArrayList<String>();
		List<String> geneNames = new ArrayList<String>();
		List<String> bioTypes = new ArrayList<String>();
		List<String> strands = new ArrayList<String>();
		List<String> regions = new ArrayList<String>();

		boolean hasBiotype = false;
		boolean hasRegion = false;
		
		for (io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene gene : gtf.findAnnotation(pos)) {
			String geneName= gene.getGeneName();
			geneIds.add(gene.getGeneId());
			geneNames.add(geneName);
			strands.add(gene.getStrand().toString());
			if (gene.getBioType() != null) {
				bioTypes.add(gene.getBioType());
				hasBiotype = true;
			} else {
				bioTypes.add(".");
			}

			GenicRegion region = gtf.findGenicRegionForPos(pos, gene.getGeneId());
			if (region != null) {
				// VCF output should use the code name for better downstream processing.
				regions.add(region.code);
				hasRegion = true;
			} else {
				regions.add(".");
			}

		}
		
		try {
			if (geneIds.size() > 0) {
//				record.getInfo().put(prefix+"GENEID", VCFAttributeValue.MISSING);
//				record.getInfo().put(prefix+"GENE", VCFAttributeValue.MISSING);
//				if (hasBiotype) {
//					record.getInfo().put(prefix+"BIOTYPE", VCFAttributeValue.MISSING);
//				}
//				record.getInfo().put(prefix+"REGION", VCFAttributeValue.MISSING);
//				record.getInfo().put(prefix+"STRAND", VCFAttributeValue.MISSING);
//			} else {
								
				record.getInfo().put(prefix+"GENEID", new VCFAttributeValue(StringUtils.join(",", geneIds)));
				record.getInfo().put(prefix+"GENE", new VCFAttributeValue(StringUtils.join(",", geneNames)));
				if (hasBiotype) {
					record.getInfo().put(prefix+"BIOTYPE", new VCFAttributeValue(StringUtils.join(",", bioTypes)));
				}
				if (hasRegion) {
					record.getInfo().put(prefix+"REGION", new VCFAttributeValue(StringUtils.join(",", regions)));
				}
				record.getInfo().put(prefix+"STRAND", new VCFAttributeValue(StringUtils.join(",", strands)));
			}
		} catch (VCFAttributeException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}