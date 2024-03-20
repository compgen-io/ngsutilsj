package io.compgen.ngsutils.pwm;

import io.compgen.ngsutils.support.SeqUtils;

public class SeqMotif extends AbstractMotifFinder {
	protected String motif;
	
	/**
	 * Motif can either be in IUPAC form (ACGT, plus single letter ambiguous codes) or 
	 * with brackets... ACGT[ACGT]ACGT => ACGTNACGT
	 * 
	 * The first step in initialization is to convert the bracketed form to single letter IUPAC
	 * 
	 * @param motif
	 * @throws Exception
	 */
	public SeqMotif(String motif, double pseudo) throws Exception {
		super();
		
		this.motif = convertToSingleLetterIUPAC(motif);
//				
//		// convert the counts to a position probability matrix (fractional frequency)
//		double[][] ppm = new double[4][singleLetterMotif.length()];
//		
//		for (int i=0; i<singleLetterMotif.length(); i++) {
//			double aFreq = 0.0;
//			double cFreq = 0.0;
//			double gFreq = 0.0;
//			double tFreq = 0.0;
//
//			switch(singleLetterMotif.charAt(i)) {
//			case 'A':
//				aFreq = 1.0;
//				break;
//			case 'C':
//				cFreq = 1.0;
//				break;
//			case 'G':
//				gFreq = 1.0;
//				break;
//			case 'T':
//				tFreq = 1.0;
//				break;
//			case 'N':
//				aFreq = 1.0;
//				cFreq = 1.0;
//				gFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'V':
//				aFreq = 1.0;
//				cFreq = 1.0;
//				gFreq = 1.0;
//				break;
//			case 'H':
//				aFreq = 1.0;
//				cFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'D':
//				aFreq = 1.0;
//				gFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'B':
//				cFreq = 1.0;
//				gFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'M':
//				aFreq = 1.0;
//				cFreq = 1.0;
//				break;
//			case 'R':
//				aFreq = 1.0;
//				gFreq = 1.0;
//				break;
//			case 'W':
//				aFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'S':
//				cFreq = 1.0;
//				gFreq = 1.0;
//				break;
//			case 'Y':
//				cFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			case 'K':
//				gFreq = 1.0;
//				tFreq = 1.0;
//				break;
//			}
//			
//			
//			ppm[0][i] = (aFreq + pseudo) / (aFreq + cFreq + gFreq + tFreq + (4*pseudo));
//			ppm[1][i] = (cFreq + pseudo) / (aFreq + cFreq + gFreq + tFreq + (4*pseudo));
//			ppm[2][i] = (gFreq + pseudo) / (aFreq + cFreq + gFreq + tFreq + (4*pseudo));
//			ppm[3][i] = (tFreq + pseudo) / (aFreq + cFreq + gFreq + tFreq + (4*pseudo));
//			
//		}
//
//		// convert the ppm to a PWM (log-likelihood, with background level correction) 
//		pwm = new double[ppm.length][ppm[0].length];
//		for (int i=0; i<ppm.length; i++) {
//			for (int j=0; j<ppm[0].length; j++) {
//				pwm[i][j] = log2(ppm[i][j] / backgroundRates[i]); // calc log2 likelihood, assume background levels as above (60% GC)
//			}
//		}
//		
//		// now that we have the final matrix, calculate the number of variations needed for permuting pvalues
////		
////		double combinations = Math.pow(4, pwm[0].length);
////		if (combinations > MAX_PERMUTATION_COUNT) {
////			this.permCount = MAX_PERMUTATION_COUNT;
////		} else {
////			this.permCount = (int) combinations;
////		}
	}
	
	
	/**
	 * Convert a bracketed motif to single letter IUPAC ACGT[ACGT]ACGT => ACGTNACGT
	 * @param motif
	 * @return
	 */
	
	private String convertToSingleLetterIUPAC(String motif) {
		String ret = "";
		boolean[] acgt = new boolean[4];
		boolean inbracket = false;
		for (int i=0; i<motif.length(); i++) {
			if (inbracket) {
				if (motif.charAt(i) == ']') {
					inbracket = false;
					ret += acgtToSingleLetter(acgt);
				} else if (motif.toUpperCase().charAt(i) == 'A') {
					acgt[0] = true;
				} else if (motif.toUpperCase().charAt(i) == 'C') {
					acgt[1] = true;
				} else if (motif.toUpperCase().charAt(i) == 'G') {
					acgt[2] = true;
				} else if (motif.toUpperCase().charAt(i) == 'T') {
					acgt[3] = true;
				}
			} else if (motif.charAt(i) == '[') {
					inbracket = true;
					acgt[0] = false;
					acgt[1] = false;
					acgt[2] = false;
					acgt[3] = false;
			} else {
				ret += motif.toUpperCase().charAt(i);
			}
		}
		return ret;
	}

	/**
	 * 
	 * N = A or C or G or T 
	 * V = A or C or G 
	 * H = A or C or T 
	 * D = A or G or T 
	 * B = C or G or T 
	 * M = A or C 
	 * R = A or G 
	 * W = A or T 
	 * S = C or G 
	 * Y = C or T
	 * K = G or T 
	 *
	 * @param acgt boolean[4] array
	 * @return
	 */
	private String acgtToSingleLetter(boolean[] acgt) {
		boolean a = acgt[0];
		boolean c = acgt[1];
		boolean g = acgt[2];
		boolean t = acgt[3];
		
		if (a && c && g && t) {
			return "N";
		} else if (a && c && g) {
			return "V";
		} else if (a && c && t) {
			return "H";
		} else if (a && g && t) {
			return "D";
		} else if (c && g && t) {
			return "B";
		} else if (a && c) {
			return "M";
		} else if (a && g) {
			return "R";
		} else if (a && t) {
			return "W";
		} else if (c && g) {
			return "S";
		} else if (c && t) {
			return "Y";
		} else if (g && t) {
			return "K";
		}
		
		return ".";
	}
	
	public double calcScore(String seq) throws Exception {
		double acc = 0.0;

		for (int i=0; i<this.motif.length(); i++) {
			if (i < seq.length()) {
				if (SeqUtils.nucleotideMatch(this.motif.charAt(i), seq.charAt(i))) {
					acc += 1.0;
				}
			}
		}
		
		return acc;
	}


	@Override
	public int getLength() {
		return this.motif.length();
	}
	
}
