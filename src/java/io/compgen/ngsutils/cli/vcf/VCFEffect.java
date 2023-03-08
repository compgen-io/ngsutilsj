package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.annotation.CodingSequence;
import io.compgen.ngsutils.annotation.CodingSequence.CodingVariant;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-effect", desc="For variants, calculate the effect of each variant on it's gene", category="vcf", experimental=true)
public class VCFEffect extends AbstractOutputCommand {
    private List<String> requiredTags = null;

	private String fastaFilename = null;
	private String vcfFilename = null;
	private String gtfFilename = null;
	
//	private boolean combineProximal = false;
	private boolean onlyOutputPass = false;
    
    
    @Option(desc="List of required tag annotations (comma-separated list)", name="gtf-tag", allowMultiple=true)
    public void setRequiredTags(String requiredTags) {
    	if (this.requiredTags == null) {
    		this.requiredTags = new ArrayList<String>();
    	}
    	for (String s:requiredTags.split(",")) {
    		this.requiredTags.add(s);
    	}
    }



    // TODO
//    @Option(desc="Combine variants that are near each other (within flanking distance, default export each separately)", name="merge")
//    public void setCombineProximal(boolean combineProximal) throws CommandArgumentException {
//        this.combineProximal = combineProximal;
//    }

    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @UnnamedArg(name = "genome.fa genes.gtf input.vcf", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames.length != 3) {
    		throw new CommandArgumentException("Invalid arguments! Missing an input file: reference FASTA, GTF, VCF");    		
    	}

    	this.fastaFilename = filenames[0];
    	this.gtfFilename = filenames[1];
    	this.vcfFilename = filenames[2];
    	
    	int stdinCount = 0;
    	for (int i=0; i< 3; i++) {
    		if (filenames[i].equals("-")) {
    			stdinCount++;
    		}
    	}
    	
    	if (stdinCount > 1) { 
    		throw new CommandArgumentException("Invalid arguments! Only one (FASTA, GTF, VCF) argument can be read from stdin");    		
    	}
    	
    	if (!new File(this.fastaFilename+".fai").exists()) { 
    		throw new CommandArgumentException("Missing FAI index for: "+this.fastaFilename);
    	}
    }


    @Exec
	public void exec() throws Exception {		
		FastaReader fasta = FastaReader.open(fastaFilename);
		System.err.print("Reading GTF... ");
		GTFAnnotationSource gtf = new GTFAnnotationSource(gtfFilename, requiredTags);
		System.err.println("done");

		VCFReader reader = null;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}

		TabWriter writer = new TabWriter(out);
		writer.write("chrom");
		writer.write("pos");
		writer.write("ref");
		writer.write("alt");
		writer.write("gene");
		writer.write("transcript_id");
		writer.write("protein_id");
		writer.write("cds_variant");
		writer.write("protein_variant");
		writer.write("consequence");
		writer.write("impact");
		writer.eol();
		
		Iterator<VCFRecord> it = reader.iterator();

		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}
			
			String chrom = rec.getChrom();
			int start = rec.getPos()-1; // zero-based
			int end = start + rec.getRef().length();
			

			Set<String> writtenPeptides = new HashSet<String>();
			for (String alt: rec.getAlt()) {
				writtenPeptides.clear();
				for (GTFGene gene : gtf.findAnnotation(new GenomeSpan(chrom, start, end))) {
					for (GTFTranscript txpt: gene.getTranscripts(true, false)) {
						CodingSequence cds = CodingSequence.buildFromTranscript(txpt, fasta);
						CodingVariant var = cds.addVariant(chrom, rec.getPos(), rec.getRef(), alt);
						String proteinId = txpt.getExons().get(0).getAttribute("protein_id");
						if (proteinId == null) {
							proteinId = "";
						}

						writer.write(chrom);
						writer.write(rec.getPos());
						writer.write(rec.getRef());
						writer.write(alt);
						writer.write(txpt.getParent().getGeneName());
						writer.write(txpt.getTranscriptId());
						writer.write(proteinId);
						writer.write(var.cdsVariant);
						writer.write(var.aaVariant);
						writer.write(var.consequence);
						writer.write(var.getImpact());
						writer.eol();
					}
				}
			}
		}

		reader.close();
		fasta.close();
	}

}
