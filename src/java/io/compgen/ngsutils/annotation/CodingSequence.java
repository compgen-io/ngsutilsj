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
		
		public CodingVariant(CodingSequence cds, String consequence, String cdsVariant, String aaVariant, int cdsPos, int aaPos) {
			this.cds = cds;
			this.consequence = consequence;
			this.cdsVariant = cdsVariant;
			this.aaVariant = aaVariant;
			this.cdsPos = cdsPos;
			this.aaPos = aaPos;
		}
	}


	public class CodingBase {
		public final String chrom;
		public final int genomePos; // zero-based
		public final int cdsPos; // zero-based
		public final int aaPos; // zero-based
		public final String seq;
		
		public CodingBase(String chrom, int genomePos, int cdsPos, int aaPos, String seq) {
			this.chrom = chrom;
			this.genomePos = genomePos; // zero-based
			this.cdsPos = cdsPos;
			this.aaPos = aaPos;
			this.seq = seq;
		}
		public CodingBase(String chrom, int pos, String seq) {
			this.chrom = chrom;
			this.genomePos = pos; // zero-based
			this.cdsPos = -1;
			this.aaPos = -1;
			this.seq = seq;
		}
		
		public CodingBase clone() {
			return new CodingBase(chrom, genomePos, cdsPos, aaPos, seq);
		}
	}
	
	private List<CodingBase> bases = new ArrayList<CodingBase>();
	private final GTFTranscript parent;
	
	public static CodingSequence buildFromTranscript(GTFTranscript txpt, FastaReader fasta) throws IOException {
		String chrom = txpt.getParent().getRef();
		CodingSequence cds = new CodingSequence(txpt);
		for (GTFExon exon: txpt.getExons()) {
			if (exon.getStart() >= txpt.getCdsStart()) {
				// exon starts in CDS
				if (exon.getEnd() <= txpt.getCdsEnd()) {
					// exon is fully within the CDS
					String seq = fasta.fetchSequence(chrom, exon.getStart(), exon.getEnd());
					cds.addRefBases(chrom, exon.getStart(), seq);
				} else if (exon.getStart() < txpt.getCdsEnd()){
					// CDS ends before exon end
					String seq = fasta.fetchSequence(chrom, exon.getStart(), txpt.getCdsEnd());
					cds.addRefBases(chrom, exon.getStart(), seq);
				}
			} else if (exon.getEnd() >= txpt.getCdsStart()) {
				// end is in CDS
				if (exon.getEnd() <= txpt.getCdsEnd()) {
					// exon ends within the CDS
					String seq = fasta.fetchSequence(chrom, txpt.getCdsStart(), exon.getEnd());
					cds.addRefBases(chrom, txpt.getCdsStart(), seq);
				} else if (exon.getEnd() > txpt.getCdsStart()) {
					// CDS is entirely within the exon
					String seq = fasta.fetchSequence(chrom, txpt.getCdsStart(), txpt.getCdsEnd());
					cds.addRefBases(chrom, txpt.getCdsStart(), seq);
				}
			}
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
		
		List<CodingBase> buf = new ArrayList<CodingBase>();
		List<CodingBase> codonbuf = new ArrayList<CodingBase>();
		buf.addAll(bases);
		
		while (buf.size()>0) {
			CodingBase cur;
			if (parent.getParent().getStrand() == Strand.PLUS) {
				cur = buf.remove(0);
			} else {
				cur = buf.remove(buf.size()-1);
			}
		
			cdsPos++;
			CodingBase newcur = new CodingBase(cur.chrom, cur.genomePos, cdsPos, -1, cur.seq);
			codonbuf.add(newcur);
			
			if (codonbuf.size() == 3) {
				aaPos++;
				while (codonbuf.size()>0) {
					CodingBase cb = codonbuf.remove(0);
					cb = new CodingBase(cb.chrom, cb.genomePos, cb.cdsPos, aaPos, cb.seq);
					if (parent.getParent().getStrand() == Strand.PLUS) {
						newBases.add(cb);
					} else {
						newBases.add(0,cb);
					}
				}
			}
		}
		while (codonbuf.size()>0) {
			CodingBase cb = codonbuf.remove(0);
			cb = new CodingBase(cb.chrom, cb.genomePos, cb.cdsPos, aaPos, cb.seq);
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
		String ret = SeqUtils.translate(getCDS(), table);
		if (ret.endsWith("X")) {
			ret = ret.substring(0, ret.length()-1);
		}
		return ret;
	}

	public String getAA(int aaPos, int flanking) {
		String peptide = getAA(SeqUtils.CodonTable.DEFAULT);
		int start = aaPos - 1 - flanking;
		int end = aaPos + flanking;
		
		if (start < 0) {
			start = 0;
		}
		if (end > peptide.length()) {
			end = peptide.length();
		}
		String ret = peptide.substring(start, end);
		if (ret.endsWith("X")) {
			ret = ret.substring(0, ret.length()-1);
		}
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
	 * @param chrom
	 * @param pos 1-based pos (from VCF)
	 * @param ref ref base(s) (from VCF)
	 * @param alt alt base(s) (from VCF)
	 * @return
	 */
	public CodingVariant addVariant(String chrom, int pos, String ref, String alt) {
		CodingSequence newcds = clone();

		int varStart = pos - 1;
		int varEnd = pos - 1 + ref.length();

		int cdsPos = -1;
		int aaPos = -1;
		
		List<CodingBase> newBases = new ArrayList<CodingBase>();
		
		boolean pre = false;
		boolean post = false;
		boolean intron = false;

		GTFExon lastExon = null;
		for (int i=0; i<parent.exons.size(); i++) {
			GTFExon exon = parent.exons.get(i);
			if (lastExon != null) {
				if (varStart > lastExon.getEnd() && varStart < exon.getStart()) {
					// we are after the last exon, but before this one -- so we are intronic
					intron = true;
					
					// TODO: if this is w/in 2 bp of the junction, flag it as a splice junction variant
				}
			}
			lastExon = exon;
		}

		if (!intron) {
			for (int i=0; i<bases.size(); i++) {
				CodingBase b = bases.get(i);
				if (!b.chrom.equals(chrom)) {
					// future support for fusions?
					newBases.add(b);
				} else if (i == 0 && varStart < b.genomePos) {
					// before first 
					pre = true;
					newBases.add(b);				
				} else if (i == bases.size()-1 && varStart > b.genomePos) {
					// after last
					post = true;
					newBases.add(b);				
				} else if (b.genomePos < varStart || b.genomePos >= varEnd) {
					// before variant or after variant
					newBases.add(b);
				} else if (b.genomePos == varStart) {
					// at the variant
					newBases.add(new CodingBase(chrom, b.genomePos, b.cdsPos, b.aaPos, alt));
					cdsPos = b.cdsPos;
					aaPos = b.aaPos;
				} else {
					// these are reference bases that we are deleting
				}
			}
		}
		
		
		String consequence="";
		String cdsVariant = "";
		String aaVariant = "";

		if (intron) {
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
		} else {
			String refCodon = "";
			String altCodon = "";
			String altPeptide = ""; // in case this is a frameshift...
			
			for (CodingBase b: bases) {
				if (b.aaPos == aaPos) {
					refCodon += b.seq;
				}
			}
	
			for (CodingBase b: newBases) {
				if (b.aaPos == aaPos) {
					altCodon += b.seq;
				}
				if (b.aaPos >= aaPos) {
					altPeptide += b.seq;
				}
			}
	
			if (parent.getParent().getStrand() == Strand.MINUS) {
				refCodon = SeqUtils.revcomp(refCodon);
				altCodon = SeqUtils.revcomp(altCodon);
				altPeptide = SeqUtils.revcomp(altPeptide);
			}
			
			String refAA = SeqUtils.translate(refCodon);
			String altAA = SeqUtils.translate(altCodon);
			
			if (altCodon.length() % 3 != 0) {
				altAA = "fs";
			}

			if (refAA.equals(altAA)) {
				altAA = "=";
			}
			
			consequence="";
			cdsVariant = "c."+cdsPos+ref+">"+alt;
			aaVariant = "p."+refAA+aaPos+altAA;

			if(alt.length() - ref.length() % 3 != 0) {
				consequence = "frameshift_variant";
			} else if (altAA.equals("=")) {
				consequence = "synonymous_variant";
			} else if (altAA.equals("X")) {
				consequence = "stop_gained";
			} else if (!altAA.equals(refAA)){
				consequence = "missense_variant";
			}
		}		
		
		newcds.bases = newBases;
		
		return new CodingVariant(newcds, consequence, cdsVariant, aaVariant, cdsPos, aaPos);
	}
	
	
	public GTFTranscript getTranscript() {
		return parent;
	}
	
}
