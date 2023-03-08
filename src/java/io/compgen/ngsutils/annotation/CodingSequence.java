package io.compgen.ngsutils.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.support.SeqUtils;
import io.compgen.ngsutils.support.SeqUtils.CodonTable;


public class CodingSequence {
	
	public class CodingVariant {
		public final CodingSequence cds;
		public final String consequence;
		public final String cdsVariant;
		public final String aaVariant;
		public final int cdsPos;
		public final int aaPos;
		public final int aaEndPos;
		
		public CodingVariant(CodingSequence cds, String consequence, String cdsVariant, String aaVariant, int cdsPos, int aaPos, int aaEndPos) {
			this.cds = cds;
			this.consequence = consequence;
			this.cdsVariant = cdsVariant;
			this.aaVariant = aaVariant;
			this.cdsPos = cdsPos;
			this.aaPos = aaPos;
			this.aaEndPos = aaEndPos;
		}
		
		// This tries to match VEP's categories as much as possible
		// https://useast.ensembl.org/info/genome/variation/prediction/predicted_data.html
		public String getImpact() {
			switch (this.consequence) {
			case "transcriptional_start_loss": // this is not in VEP
			case "splice_acceptor_variant":
			case "splice_donor_variant":
			case "stop_gained":
			case "stop_loss":
			case "frameshift_variant":
			case "start_lost":
				return "HIGH";
			case "inframe_insertion":
			case "inframe_deletion":
			case "missense_variant":
				return "MODERATE";
			case "synonymous_variant":
				return "LOW";
			case "5_prime_UTR_variant":
			case "3_prime_UTR_variant":
			default:
				return "MODIFIER";
			}
		}
	}


	public class CodingBase {
		public final String chrom;
		public final int genomePos; // zero-based
		public final int cdsPos; // zero-based
		public final int aaPos; // zero-based
		public final int codonPos;
		public final String seq;
		
		public CodingBase(String chrom, int genomePos, int cdsPos, int aaPos, int codonPos, String seq) {
			this.chrom = chrom;
			this.genomePos = genomePos; // zero-based
			this.cdsPos = cdsPos;
			this.aaPos = aaPos;
			this.codonPos = codonPos;
			this.seq = seq;
		}
		public CodingBase(String chrom, int pos, String seq) {
			this.chrom = chrom;
			this.genomePos = pos; // zero-based
			this.cdsPos = -1;
			this.aaPos = -1;
			this.codonPos = -1;
			this.seq = seq;
		}
		
		public CodingBase clone() {
			return new CodingBase(chrom, genomePos, cdsPos, aaPos, codonPos, seq);
		}
		
		public String toString() {
			return seq+" cds:"+cdsPos+",aa:"+aaPos+",orf:"+codonPos;
		}
	}
	
	private List<CodingBase> bases = new ArrayList<CodingBase>();
	private final GTFTranscript parent;
	
	public static CodingSequence buildFromTranscript(GTFTranscript txpt, FastaReader fasta) throws IOException {
		String chrom = txpt.getParent().getRef();
		CodingSequence cds = new CodingSequence(txpt);
		for (GTFExon exon: txpt.getExons()) {
			
			// Add the full sequence for each exon -- we'll trim it to start/stop codons when we calculate the AA/CDS positions. 
			String seq = fasta.fetchSequence(chrom, exon.getStart(), exon.getEnd());
			cds.addRefBases(chrom, exon.getStart(), seq);					

//			if (exon.getStart() >= txpt.getCdsStart()) {
//				// exon starts inside CDS
//				if (exon.getEnd() <= txpt.getCdsEnd()) {
//					// exon is fully within the CDS
//					String seq = fasta.fetchSequence(chrom, exon.getStart(), exon.getEnd());
//					cds.addRefBases(chrom, exon.getStart(), seq);
//				} else if (exon.getStart() < txpt.getCdsEnd()){
//					// CDS ends before exon end
//					String seq = fasta.fetchSequence(chrom, exon.getStart(), txpt.getCdsEnd());
//					cds.addRefBases(chrom, exon.getStart(), seq);
//				}
//			} else if (exon.getEnd() >= txpt.getCdsStart()) {
//				// end is in CDS
//				if (exon.getEnd() <= txpt.getCdsEnd()) {
//					// exon ends within the CDS
//					String seq = fasta.fetchSequence(chrom, txpt.getCdsStart(), exon.getEnd());
//					cds.addRefBases(chrom, txpt.getCdsStart(), seq);
//				} else if (exon.getEnd() > txpt.getCdsStart()) {
//					// CDS is entirely within the exon
//					String seq = fasta.fetchSequence(chrom, txpt.getCdsStart(), txpt.getCdsEnd());
//					cds.addRefBases(chrom, txpt.getCdsStart(), seq);
//				}
//			}
		}
		cds.calcCDSAAPos();
		return cds;
		
	}

	
	private CodingSequence(GTFTranscript parent) {
		this.parent = parent;
	}
	
	private void addRefBases(String chrom, int start, String seq) {
		for (int i=0; i<seq.length(); i++) {
			this.bases.add(new CodingBase(chrom, start + i, ""+seq.charAt(i)));
		}
	}
	
	private void addBase(CodingBase b) {
		this.bases.add(b);
	}
	
	private void calcCDSAAPos() {
		List<CodingBase> newBases = new ArrayList<CodingBase>();

		int cdsPos = 0;
		int aaPos = 0;
		int codonPos = 0;
		
		List<CodingBase> buf = new ArrayList<CodingBase>();
		List<CodingBase> codonbuf = new ArrayList<CodingBase>();
		buf.addAll(bases);
		
		while (buf.size()>0) {
			CodingBase cur;
			if (parent.getParent().getStrand() == Strand.PLUS) {
				cur = buf.remove(0);
				
				
				// is this base w/in the coding range?
				if (cur.genomePos < parent.getStartCodonStart()) {
					// too early
					continue;
				}
				if (cur.genomePos >= parent.getStopCodonEnd()) {
					// too late
					continue;
				}
			} else {
				// pull from the end to get CDS/AA pos right
				cur = buf.remove(buf.size()-1);
				
				
				// is this base w/in the coding range?
				if (cur.genomePos < parent.getStopCodonStart()) {
					// too early
					continue;
				}
				if (cur.genomePos >= parent.getStartCodonEnd()) {
					// too late
					continue;
				}
			}			
			
			cdsPos++;
			codonPos++;
			CodingBase newcur = new CodingBase(cur.chrom, cur.genomePos, cdsPos, -1, codonPos, cur.seq);
			// this buffer holds one codon's worth of bases
			codonbuf.add(newcur);
			
			if (codonbuf.size() == 3) {
				aaPos++;
				codonPos = 0;
				while (codonbuf.size()>0) {
					CodingBase cb = codonbuf.remove(0);
					cb = new CodingBase(cb.chrom, cb.genomePos, cb.cdsPos, aaPos, cb.codonPos, cb.seq);
					if (parent.getParent().getStrand() == Strand.PLUS) {
						newBases.add(cb);
					} else {
						// add to the end
						newBases.add(0,cb);
					}
				}
			}
		}
		while (codonbuf.size()>0) {
			CodingBase cb = codonbuf.remove(0);
			cb = new CodingBase(cb.chrom, cb.genomePos, cb.cdsPos, aaPos, cb.codonPos, cb.seq);
			if (parent.getParent().getStrand() == Strand.PLUS) {
				newBases.add(cb);
			} else {
				newBases.add(0,cb);
			}
		}
		
		
		this.bases = newBases;

	}

	public String getAA() {
		return getAA(SeqUtils.CodonTable.DEFAULT);
	}
	public String getAA(CodonTable table) {
		String ret = SeqUtils.translate(getCDS(), table, true);
//		if (ret.endsWith("*")) {
//			ret = ret.substring(0, ret.length()-1);
//		}
		return ret;
	}

	public String getAA(int aaPos) {
		return getAA(aaPos, aaPos, 0); 
	}

	public String getAA(int aaPos, int flanking) {
		return getAA(aaPos, aaPos, flanking);
	}

	public String getAA(int aaPos, int endPos, int flanking) {
		String peptide = getAA(SeqUtils.CodonTable.DEFAULT);
		int start = aaPos - 1 - flanking;
		int end = endPos + flanking;
		
		if (start < 0) {
			start = 0;
		}
		if (end > peptide.length()) {
			end = peptide.length();
		}
		String ret = peptide.substring(start, end);
//		if (ret.endsWith("*")) {
//			ret = ret.substring(0, ret.length()-1);
//		}
		return ret;
	}

	
	public String getCDS() {
		String s = "";
		for (CodingBase b: bases) {
			s += b.seq;
		}
		
		if (parent.getParent().getStrand() == Strand.MINUS) {
			s = SeqUtils.revcomp(s);
		}
		
		return s;
	}
	
	public CodingSequence clone() {
		CodingSequence newcds = new CodingSequence(this.parent);
		for (CodingBase frag: bases) {
			newcds.addBase(frag.clone());
		}
		return newcds;
	}
	
	/**
	 * 
	 * Junction boundary is 2 bp.
	 * 
	 * @param chrom
	 * @param pos 1-based pos (from VCF)
	 * @param ref ref base(s) (from VCF)
	 * @param alt alt base(s) (from VCF)
	 * @return
	 */
	public CodingVariant addVariant(String chrom, int pos, String ref, String alt) {
//		System.out.println(this.parent.getTranscriptId() + " / Adding variant: "+ref+">"+alt);
		CodingSequence newcds = clone();

		int varStart = pos - 1;
		int varEnd = pos - 1 + ref.length();

		int cdsPos = -1;
		int cdsEnd = -1;
		int aaPos = -1;
		int aaEndPos = -1;
		
		List<CodingBase> newBases = new ArrayList<CodingBase>();
		
		boolean pre = false;
		boolean post = false;
		boolean intron = false;
		boolean junction_5 = false;
		boolean junction_3 = false;

		boolean span_5 = false;
		boolean span_3 = false;
		
		// First, check to see if the variant falls within an exon for this transcript
		GTFExon lastExon = null;
		for (int i=0; i<parent.exons.size(); i++) {
			GTFExon exon = parent.exons.get(i);

//			System.out.println(exon.getStart() + " / " + exon.getEnd());
			if (lastExon == null) {
				if (varStart < exon.getStart() && varEnd > exon.getStart()) {
					// we are spanning the 5' end here
					span_5 = true;
				}
			} else {
//				if (varStart > lastExon.getEnd() && varStart < exon.getStart() && varEnd >= exon.getStart()) {
//					// starts in the intron, and extends past exon boundary (probably removing splice site)
//					System.err.println("ZZZ - exon spanning deletion1 => " + varStart +"/"+ lastExon.getEnd()+"; "+varEnd +"/" + exon.getStart()+" => "+chrom+":"+pos+":"+ref+">"+alt);
//				}
//				if (varStart <= exon.getEnd() && varEnd > exon.getEnd()) {
//					// starts in the this exon, extending past the end, into the intron (probably removing splice site)
//					System.err.println("ZZZ - exon spanning deletion3 => " + varStart +"/"+ lastExon.getEnd()+"; "+varEnd +"/" + exon.getStart()+" => "+chrom+":"+pos+":"+ref+">"+alt);
//				}

				if (varStart > lastExon.getEnd() && varEnd < exon.getStart()) {
					// we are after the last exon, but before this one -- so we are intronic
					intron = true;
//					System.out.println("INTRON!");
					
					// any variant w/in 2bp of an exon boundary will affect splicing as it will
					// impact the canonical donor/acceptor (GT/AG)
					if (varStart - lastExon.getEnd() <= 2) {
//						System.out.println("JUNCTION!");
						junction_5 = true;
					} else if (exon.getStart() - varEnd <= 2) {
//						System.out.println("JUNCTION!");
						junction_3 = true;
					}
				}
			}
			lastExon = exon;
		}
		if (varStart < lastExon.getEnd() && varEnd > lastExon.getEnd()) {
			// we are spanning the 3' end here
			span_3 = true;
		}

//		List<CodingBase> removed = new ArrayList<CodingBase>();
//		List<CodingBase> added = new ArrayList<CodingBase>();
		
		// If we are in an exon, then figure out the new cds/aa sequences
		// the variant could be before the CDS (5' UTR) or after CDS (3' UTR)
		if (!intron) {
			for (int i=0; i<bases.size(); i++) {
				CodingBase b = bases.get(i);
				if (!b.chrom.equals(chrom)) {
					// skip for now... FUTURE: support for fusions?
					newBases.add(b);
				} else if (i == 0 && varStart < b.genomePos) {
					// variant is before first CDS base 
					pre = true;
					newBases.add(b);				
				} else if (i == bases.size()-1 && varStart > b.genomePos) {
					// after last CDS base
					post = true;
					newBases.add(b);				
				} else if (b.genomePos < varStart || b.genomePos >= varEnd) {
					// in CDS, but before/after variant
					newBases.add(b);
				} else if (b.genomePos == varStart) {
					// at the variant
					CodingBase nb = new CodingBase(chrom, b.genomePos, b.cdsPos, b.aaPos, b.codonPos, alt);
					newBases.add(nb);
					cdsPos = b.cdsPos;
					cdsEnd = b.cdsPos;
					aaPos = b.aaPos;
					aaEndPos = b.aaPos;

//					added.add(nb);
//
//					if (parent.getParent().getStrand() == Strand.PLUS) {
//						removed.add(b);
//					} else {
//						removed.add(0, b);
//					}
//					System.out.println("Added base: " + nb);
//					System.out.println("Removing base: " + b);
				} else {
					// b.genomePos > varStart && b.genomePos < varEnd
					// so these are reference bases that we are deleting
					cdsEnd = b.cdsPos;
					aaEndPos = b.aaPos;
//					if (parent.getParent().getStrand() == Strand.PLUS) {
//						removed.add(b);
//					} else {
//						removed.add(0, b);
//					}
//					System.out.println("Removing base: " + b);
				}
			}
		}

//		System.out.println("Original AA: " + this.getAA());
		CodingSequence newSeq = new CodingSequence(this.parent);
		for (CodingBase b: newBases) {
			newSeq.addBase(b);
		}
//		System.out.println("New      AA: " + newSeq.getAA());
		
		String consequence="";
		String cdsVariant = "";
		String aaVariant = "";

		if (span_5 && parent.getParent().getStrand() == Strand.PLUS) {
			// only flag TSS loss when at the start of the gene
			consequence = "transcriptional_start_loss";
		} else if (span_3 && parent.getParent().getStrand() == Strand.MINUS) {
			// only flag TSS loss when at the start of the gene
			consequence = "transcriptional_start_loss";
		} else if (junction_5) {
			if (parent.getParent().getStrand() == Strand.PLUS) {
				consequence = "splice_donor_variant";
			} else {
				consequence = "splice_acceptor_variant";
			}
		} else if (junction_3) {
				if (parent.getParent().getStrand() == Strand.PLUS) {
					consequence = "splice_acceptor_variant";
				} else {
					consequence = "splice_donor_variant";
				}
		} else if (intron) {
				consequence = "intron_variant";
		} else if (pre) {
			if (parent.getParent().getStrand() == Strand.PLUS) {
				consequence = "5_prime_UTR_variant";
			} else {
				consequence = "3_prime_UTR_variant";
			}
		} else if (post) {
			if (parent.getParent().getStrand() == Strand.PLUS) {
				consequence = "3_prime_UTR_variant";
			} else {
				consequence = "5_prime_UTR_variant";
			}
		} else if (alt.length() < ref.length()) {
//			System.out.println("Deletion");
				// deletion

				if (parent.getParent().getStrand() == Strand.PLUS) {
					cdsVariant = "c."+cdsPos+"_"+cdsEnd+"del"+ref.substring(alt.length());
				} else {
					cdsVariant = "c."+cdsEnd+"_"+cdsPos+"del"+SeqUtils.revcomp(ref.substring(alt.length())); 
				}

				String refAA = this.getAA();
				String altAA = newSeq.getAA();
				
				int startPos = 0;
				int endPos = refAA.length();
				String startAA = "";
				String endAA = "";

				startPos = 0;
				while (refAA.length() > 0 && altAA.length()>0 && refAA.charAt(0) == altAA.charAt(0)) {
					startPos ++;
					refAA = refAA.substring(1);
					altAA = altAA.substring(1);
				}

				// we removed the common prefix, so the deletion starts here
				startPos ++;
				if (refAA.length() > 0) {
					startAA = ""+refAA.charAt(0);
				} else {
					startAA = "";
				}

				
				if (alt.length() - ref.length() % 3 != 0) {
					// frameshift deletion
					consequence = "frameshift_variant";

					if (altAA.length()>0) {
						endAA = ""+altAA.charAt(0);
					}
					int fsLen = altAA.length(); // the ter is at altAA.length()					
					aaVariant = "p."+startAA+startPos+endAA+"fs*"+fsLen;
				} else {
					// inframe del
					consequence = "inframe_deletion";

					while (refAA.length() > 0 && altAA.length()>0 && refAA.charAt(refAA.length()-1) == altAA.charAt(altAA.length()-1)) {
						endPos --;
						refAA = refAA.substring(0,refAA.length()-1);
						altAA = altAA.substring(0,altAA.length()-1);
					}
	
					endAA = "";
					if (refAA.length()>0) {
						refAA.substring(refAA.length()-1,refAA.length());
					}
					aaVariant = "p."+startAA+startPos;
					if (endPos - startPos > 1) {
						aaVariant += "_"+endAA+endPos;
					}
					aaVariant += "del";							
				}

//				System.out.println("del: start:"+startAA+startPos+", end:"+endAA+endPos + " other: "+aaPos+"-"+aaEndPos);
				
		} else if (alt.length() > ref.length()) {
//			System.out.println("Insertion");
			// inserts

			if (parent.getParent().getStrand() == Strand.PLUS) {
				cdsVariant = "c."+cdsPos+"ins"+alt.substring(ref.length());
			} else {
				cdsVariant = "c."+cdsPos+"ins"+SeqUtils.revcomp(alt.substring(ref.length())); 
			}

			String refAA = this.getAA();
			String altAA = newSeq.getAA();
			
			int startPos = 0;
			int endPos = refAA.length();
			String startAA = "";
			String endAA = "";

			startPos = 0;
			while (refAA.length() > 0 && altAA.length()>0 && refAA.charAt(0) == altAA.charAt(0)) {
				startPos ++;
				refAA = refAA.substring(1);
				altAA = altAA.substring(1);
			}

			// we removed the common prefix, so the deletion starts here
			startPos ++;
			startAA = "";
			if (refAA.length() > 0) {
				startAA += refAA.charAt(0);
			}

			if (alt.length() - ref.length() % 3 != 0) {
				// frameshift insertion
				consequence = "frameshift_variant";

				endAA = "";
				if (altAA.length() > 0) {
					endAA += altAA.charAt(0);
				}
				int fsLen = altAA.length(); // the ter is at altAA.length()					
				aaVariant = "p."+startAA+startPos+endAA+"fs*"+fsLen; 
			} else {
				// inframe ins
				consequence = "inframe_insertion";

				while (refAA.length() > 0 && altAA.length()>0 && refAA.charAt(refAA.length()-1) == altAA.charAt(altAA.length()-1)) {
					endPos --;
					refAA = refAA.substring(0,refAA.length()-1);
					altAA = altAA.substring(0,altAA.length()-1);
				}

				endAA = refAA.substring(refAA.length()-1,refAA.length());
				aaVariant = "p."+startAA+startPos;
				if (endPos - startPos > 1) {
					aaVariant += "_"+endAA+endPos;
				}
				aaVariant += "ins" + altAA;							

			}				
//			System.out.println("ins: start:"+startAA+startPos+", end:"+endAA+endPos + " other: "+aaPos+"-"+aaEndPos);

		} else {
//			System.out.println("SNV");
			// missense
			String refCodon = "";
			String altCodon = "";
			
			for (CodingBase b: bases) {
				if (b.aaPos == aaPos) {
					refCodon += b.seq;
				}
			}
	
			for (CodingBase b: newBases) {
				if (b.aaPos == aaPos) {
					altCodon += b.seq;
				}
//					if (b.aaPos >= aaPos) {
//						altPeptide += b.seq;
//					}
			}
	
			if (parent.getParent().getStrand() == Strand.MINUS) {
				refCodon = SeqUtils.revcomp(refCodon);
				altCodon = SeqUtils.revcomp(altCodon);
			}
			
			String refAA = SeqUtils.translate(refCodon, false);
			String altAA = SeqUtils.translate(altCodon, true);
//			
//			System.out.println("refCodon   : " + refCodon + " => " + refAA);
//			System.out.println("altCodon   : " + altCodon + " => " + altAA);

			cdsVariant = "c."+cdsPos+ref+">"+alt;

			if (refAA.equals(altAA)) {
				consequence = "synonymous_variant";
				aaVariant = "p."+refAA+aaPos+"=";
			} else if (altAA.equals("*")) {
				consequence = "stop_gained";
				aaVariant = "p."+refAA+aaPos+"*";
			} else if (refAA.equals("*")) {
				consequence = "stop_lost";
				aaVariant = "p."+refAA+aaPos+altAA;
			} else if (!altAA.equals(refAA)){
				consequence = "missense_variant";
				aaVariant = "p."+refAA+aaPos+altAA;
			}
		}

		if (aaPos == 1 && !consequence.contentEquals("synonymous_variant")) {
			consequence = "start_lost";
		}
		
//		System.out.println("Consequence: " + consequence);
//		System.out.println("cdsVariant : " + cdsVariant);
//		System.out.println("aaVariant  : " + aaVariant);
//
		
		newcds.bases = newBases;
		
		return new CodingVariant(newcds, consequence, cdsVariant, aaVariant, cdsPos, aaPos, aaEndPos);
	}
	
	
	public GTFTranscript getTranscript() {
		return parent;
	}
	
}
