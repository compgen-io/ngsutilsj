package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import io.compgen.ngsutils.annotation.CodingSequence;
import io.compgen.ngsutils.annotation.CodingSequence.CodingVariant;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-peptide", desc="For variants, extract new peptides (SNV or indel, in coding regions, does not handle splicing)", category="vcf", experimental=true)
public class VCFPeptide extends AbstractOutputCommand {
	private String fastaFilename = null;
	private String wtFastaOutname = null;
    private List<String> requiredGTFTags = null;
    private List<String> infoValues = null;
    private List<String> formatValues = null;

	private String vcfFilename = null;
	private String gtfFilename = null;
	
	private int flankingLength = 10;
//	private boolean combineProximal = false;
	private boolean onlyOutputPass = false;
    
    
    @Option(desc="Number of flanking AAs to include (set to -1 to output full protein)", name="flanking", defaultValue="10")
    public void setFlankingLength(int flankingLength) throws CommandArgumentException {
        this.flankingLength = flankingLength;
    }
    @Option(desc="List of required tag annotations (comma-separated list)", name="gtf-tag", allowMultiple=true)
    public void setRequiredTags(String requiredTags) {
    	if (this.requiredGTFTags == null) {
    		this.requiredGTFTags = new ArrayList<String>();
    	}
    	for (String s:requiredTags.split(",")) {
    		this.requiredGTFTags.add(s);
    	}
    }

    @Option(desc="List of INFO annotations to be added to the FASTA file (comma-separated list)", name="info", allowMultiple=true)
    public void setInfoValues(String infoValues) {
    	if (this.infoValues == null) {
    		this.infoValues = new ArrayList<String>();
    	}
    	for (String s:infoValues.split(",")) {
    		this.infoValues.add(s);
    	}
    }

    @Option(desc="List of FORMAT annotations to be added to the FASTA file (comma-separated list, SAMPLE:NAME{:allele})", name="format", allowMultiple=true)
    public void setFormatValues(String formatValues) throws CommandArgumentException {
    	if (this.formatValues == null) {
    		this.formatValues = new ArrayList<String>();
    	}
    	for (String s:formatValues.split(",")) {
    		if (!s.contains(":")) {
    			throw new CommandArgumentException("Missing SAMPLE:KEY for format value");
    		}
    		this.formatValues.add(s);
    	}
    }


    // TODO
//    @Option(desc="Combine variants that are near each other (within flanking distance, default export each separately)", name="merge")
//    public void setCombineProximal(boolean combineProximal) throws CommandArgumentException {
//        this.combineProximal = combineProximal;
//    }

    @Option(desc="Output native (reference) peptides to this FASTA output", name="native")
    public void setNativeFastaOutname(String nativeFastaOutname) throws CommandArgumentException {
        this.wtFastaOutname = nativeFastaOutname;
    }
    
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

    // TODO: Make this work for large indels from SV VCFs.
    //       Make this work for Fusion other SVs as well (not from VCF?)
    
	@Exec
	public void exec() throws Exception {		
		FastaReader fasta = FastaReader.open(fastaFilename);
		System.err.print("Reading GTF... ");
		GTFAnnotationSource gtf = new GTFAnnotationSource(gtfFilename, requiredGTFTags);
		System.err.println("done");

		VCFReader reader = null;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}
		Iterator<VCFRecord> it = reader.iterator();

		PrintStream ps = new PrintStream(out);

		PrintStream ps2 = null;
		if (wtFastaOutname != null) {
			if (wtFastaOutname.equals("-")) {
				if (out == System.out)  {
					ps2 = ps;
				} else {
					ps2 = new PrintStream(System.out);
				}
			} else if (wtFastaOutname != null){
				ps2 = new PrintStream(new FileOutputStream(wtFastaOutname));
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

					System.err.println(gene.getGeneName() + " / Variant: "+chrom + ":" + rec.getPos() + " "+rec.getRef()+">"+alt);

					for (GTFTranscript txpt: gene.getTranscripts(true, false)) { // only look for coding transcripts
						PeptideRecord peptide = writePeptide(chrom, rec.getPos(), rec.getRef(), alt, txpt, fasta, rec);
						
						if (peptide != null) {
							if (!writtenPeptides.contains(peptide.peptide)) {
								ps.println(">"+peptide.header);
								if (peptide.peptide.endsWith("*")) {
									ps.println(peptide.peptide.substring(0, peptide.peptide.length()-1));
								} else {
									ps.println(peptide.peptide);
								}
								
								if (ps2 != null) {
									ps2.println(">"+peptide.refheader);
									if (peptide.refpep.endsWith("*")) {
										ps2.println(peptide.refpep.substring(0, peptide.refpep.length()-1));
									} else {
										ps2.println(peptide.refpep);
									}
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
	
	private PeptideRecord writePeptide(String chrom, int pos, String ref, String alt, GTFTranscript txpt, FastaReader fasta, VCFRecord vcfRecord) throws IOException, VCFAttributeException {
		CodingSequence cds = CodingSequence.buildFromTranscript(txpt, fasta);
		CodingVariant var = cds.addVariant(chrom, pos, ref, alt);

		
		int start = pos - 1;
		int end = start + ref.length();
		
		String proteinId = txpt.getExons().get(0).getAttribute("protein_id");
		if (proteinId != null) {
			// only report out variants that are part of coding proteins...

			String header = chrom+":"+start+"-"+end+"|"+ref+">"+alt+"|" + txpt.getParent().getGeneName() + 
				    "|"+txpt.getTranscriptId()+"|"+proteinId + "|"+var.cdsVariant + "|" + var.aaVariant + "|" + var.consequence;
			
			String refheader = chrom+":"+start+"-"+end+"||" + txpt.getParent().getGeneName() + 
				    "|"+txpt.getTranscriptId()+"|"+proteinId + "|||reference_sequence";
					

			if (this.infoValues != null) {
				for (String key: this.infoValues) {
					header += "|";
					refheader += "|";

					String infoKey = key;
					String alleleName = null;
					
					if (key.contains(":")) {
						String[] spl = key.split(":");
						infoKey = spl[0];
						alleleName = spl[1];
					}
					
					VCFAttributeValue value = vcfRecord.getInfo().get(infoKey);
					if (value != null) {
						if (value == VCFAttributeValue.EMPTY) {
							// this is a flag
							header += key;
							refheader += key;
						} else {
							header += infoKey + "=" + value.asString(alleleName);
							refheader += infoKey + "=" + value.asString(alleleName);
						}
					}
				}
			}
			
			if (this.formatValues != null) {
				for (String key: this.formatValues) {
					header += "|";
					refheader += "|";

					String sample;
					String keyName;
					String alleleName=null;

					String[] spl = key.split(":", 3);
					sample = spl[0];
					keyName = spl[1];
					if (spl.length>2) {
						alleleName = spl[2];
					}
					
					
					VCFAttributeValue value = vcfRecord.getFormatBySample(sample).get(keyName);
					if (value != null) {
						// Format doesn't allow Flags
						header += key + "=" + value.asString(alleleName);
						refheader += key + "=" + value.asString(alleleName);
					}
				}
			}

			String peptide = null;
			String refpep = null;

			if (var.consequence.equals("missense_variant")) {
				if (var.aaPos > -1) {
					if (flankingLength == -1) {
						refpep = cds.getAA();
						peptide = var.cds.getAA();
					} else {
						refpep = cds.getAA(var.aaPos, flankingLength);
						peptide = var.cds.getAA(var.aaPos, flankingLength);
					}
				}
			} else if (var.consequence.equals("inframe_deletion")) {
				if (var.aaPos > -1) {
					if (flankingLength == -1) {
						refpep = cds.getAA();
						peptide = var.cds.getAA();
					} else {
						refpep = cds.getAA(var.aaPos, var.aaEndPos, flankingLength);
						peptide = var.cds.getAA(var.aaPos, flankingLength);
					}
				}
			} else if (var.consequence.equals("inframe_insertion")) {
				// this is untested
				if (var.aaPos > -1) {
					if (flankingLength == -1) {
						refpep = cds.getAA();
						peptide = var.cds.getAA();
					} else {
						refpep = cds.getAA(var.aaPos, var.aaEndPos, flankingLength);
						peptide = var.cds.getAA(var.aaPos, flankingLength);
					}
				}
			} else if (var.consequence.equals("frameshift_variant")) {
				if (var.aaPos > -1) {
					if (flankingLength == -1) {
						refpep = cds.getAA();
						peptide = var.cds.getAA();
					} else {
						refpep = cds.getAA(var.aaPos, var.aaEndPos, flankingLength);
						// frameshifts will result in a lot of different new AAs, so just return 
						// everything from the FS to the end (remove Ter/*)
						peptide = var.cds.getAA().substring(Math.max(0, var.aaPos - flankingLength));
						if (peptide.endsWith("*")) {
							peptide = peptide.substring(0, peptide.length()-1);
						}
					}
				}
			}
			
			if (peptide != null) {
				return new PeptideRecord(header, peptide, refheader, refpep);
			}
		}
		return null;
	}

}
