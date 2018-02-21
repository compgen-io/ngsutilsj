package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;

import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class FlankingBases extends AbstractBasicAnnotator {
	
	protected IndexedFastaFile ref;
	protected int size;
	
	public FlankingBases(String fasta, int size) throws IOException {
		ref = new IndexedFastaFile(fasta);
		this.size = size;
	}
	
	public FlankingBases(String fasta) throws IOException {
		this(fasta, 1);
	}
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.info("CG_FLANKING", "1", "String", "+/-1 bp flanking the variant (no indels)");
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		header.addFormat(getAnnotationType());
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		// don't annotated indels
		if (record.getRef().length()>1) {
			return;
		}
		
		for (String alt: record.getAlt()) {
			if (alt.length()>1) {
				return;
			}
		}

		try {
			String before = ref.fetchSequence(record.getChrom(), record.getPos()-1-size, record.getPos()+size);
			record.getInfo().put("CG_FLANKING", new VCFAttributeValue(before));

		} catch (IOException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}