package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.CodingSequence;
import io.compgen.ngsutils.annotation.CodingSequence.CodingVariant;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-peptide", desc="For variants, extract new peptides (SNV or indel, in coding regions, does not handle splicing)", category="vcf", experimental=true)
public class VCFPeptide extends AbstractOutputCommand {
	private String fastaFilename = null;
	private String novelFastaOutname = null;
	private String nativeFastaOutname = null;
	
	private String vcfFilename = null;
	private String gtfFilename = null;
	
	private int flankingLength = 9;
//	private boolean combineProximal = false;
	private boolean onlyOutputPass = false;
    
    
    @Option(desc="Number of flanking AAs to include (set to -1 to output full protein)", name="flanking", defaultValue="8")
    public void setFlankingLength(int flankingLength) throws CommandArgumentException {
        this.flankingLength = flankingLength;
    }

    // TODO
//    @Option(desc="Combine variants that are near each other (within flanking distance, default export each separately)", name="merge")
//    public void setCombineProximal(boolean combineProximal) throws CommandArgumentException {
//        this.combineProximal = combineProximal;
//    }

    @Option(desc="Output native (reference) peptides to this FASTA output", name="native")
    public void setNativeFastaOutname(String nativeFastaOutname) throws CommandArgumentException {
        this.nativeFastaOutname = nativeFastaOutname;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @UnnamedArg(name = "genome.fa genes.gtf input.vcf output.fa", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames.length != 4) {
    		throw new CommandArgumentException("Invalid arguments! Missing an input file: reference FASTA, GTF, VCF, or peptide FASTA");    		
    	}

    	this.fastaFilename = filenames[0];
    	this.gtfFilename = filenames[1];
    	this.vcfFilename = filenames[2];
    	this.novelFastaOutname = filenames[3];
    	
    	// the output can also be written to stdout, so only check the first three
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
		GTFAnnotationSource gtf = new GTFAnnotationSource(gtfFilename);
		System.err.println("done");

		VCFReader reader = null;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}
		Iterator<VCFRecord> it = reader.iterator();

		PrintStream ps;
		if (novelFastaOutname.equals("-") || novelFastaOutname == null) {
			ps = new PrintStream(out);
		} else {
			ps = new PrintStream(new FileOutputStream(novelFastaOutname));
		}

		PrintStream ps2 = null;
		if (nativeFastaOutname != null) {
			if (nativeFastaOutname.equals(novelFastaOutname)) {
				ps2 = ps;
			} else if (nativeFastaOutname.equals("-")) {
				ps2 = new PrintStream(out);
			} else if (nativeFastaOutname != null){
				ps2 = new PrintStream(new FileOutputStream(nativeFastaOutname));
			}
		}
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
						PeptideRecord peptide = writePeptide(chrom, rec.getPos(), rec.getRef(), alt, txpt, fasta);
						
						if (peptide != null) {
							if (!writtenPeptides.contains(peptide.peptide)) {
								ps.println(">"+peptide.header);
								ps.println(peptide.peptide);
								
								if (ps2 != null) {
									ps2.println(">"+peptide.refheader);
									ps2.println(peptide.refpep);
								}
								writtenPeptides.add(peptide.peptide);
							}
						}
						
					}
				}
			}
		}

		reader.close();
		fasta.close();
		ps.close();
	}

	public class PeptideRecord {
		public final String header;
		public final String peptide;
		public final String refheader;
		public final String refpep;
		
		private PeptideRecord(String header, String peptide, String refheader, String refpep) {
			this.header = header;
			this.peptide = peptide;
			this.refheader = refheader;
			this.refpep = refpep;
		}
	}
	
	private PeptideRecord writePeptide(String chrom, int pos, String ref, String alt, GTFTranscript txpt, FastaReader fasta) throws IOException {
		CodingSequence cds = CodingSequence.buildFromTranscript(txpt, fasta);
		CodingVariant var = cds.addVariant(chrom, pos, ref, alt);

		int start = pos - 1;
		int end = start + ref.length();
		
		String proteinId = txpt.getExons().get(0).getAttribute("protein_id");
		if (proteinId == null) {
			proteinId = "";
		}

		if (var.consequence.equals("missense_variant")) {
			String header = chrom+":"+start+"-"+end+"|"+ref+">"+alt+"|" + txpt.getParent().getGeneName() + 
				    "|"+txpt.getTranscriptId()+"|"+proteinId + "|"+var.cdsVariant + "|" + var.aaVariant + "|" + var.consequence;
			String refheader = chrom+":"+start+"-"+end+"||" + txpt.getParent().getGeneName() + 
				    "|"+txpt.getTranscriptId()+"|"+proteinId + "|||reference_sequence";
		
			String peptide = "";
			String refpep = "";
			
			if (var.aaPos > -1) {
				if (flankingLength == -1) {
					refpep = cds.getAA();
					peptide = var.cds.getAA();
				} else {
					refpep = cds.getAA(var.aaPos, flankingLength);
					peptide = var.cds.getAA(var.aaPos, flankingLength);
				}
				return new PeptideRecord(header, peptide, refheader, refpep);
			}
		}
		return null;
	}

}
