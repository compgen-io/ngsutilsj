package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.support.SeqUtils;
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
	

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		try {
            header.addInfo(VCFAnnotationDef.info("CG_FLANKING", "1", "String", "+/- "+ size+ " bp flanking the variant (no indels)"));
            header.addInfo(VCFAnnotationDef.info("CG_FLANKING_SUB", "A", "String", "Substitution caused by variant "));
        } catch (VCFParseException e) {
            throw new VCFAnnotatorException(e);
        }
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
			String refSeq = ref.fetchSequence(record.getChrom(), record.getPos()-1-size, record.getPos()+size);
			record.getInfo().put("CG_FLANKING", new VCFAttributeValue(refSeq));

			boolean revcomp = false;
			
			if (record.getRef().toUpperCase().equals("A") || record.getRef().toUpperCase().equals("G")) {
			    refSeq = SeqUtils.revcomp(refSeq);
			    revcomp = true;
			}
			
			String pre = refSeq.substring(0,size);
			String var = refSeq.substring(size,size+1);
			String post = refSeq.substring(size+1);
			
            List<String> outs = new ArrayList<String>();
			for (String alt: record.getAlt()) {
			    if (revcomp) {
			        alt = SeqUtils.revcomp(alt);
			    }
                outs.add(pre + "[" + var + ">" + alt + "]" + post);
			}
			
            record.getInfo().put("CG_FLANKING_SUB", new VCFAttributeValue(StringUtils.join(",", outs)));
			
		} catch (IOException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}