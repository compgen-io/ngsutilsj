package io.compgen.ngsutils.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import io.compgen.common.StringUtils;

public class SeqUtils {
    public static class CodonTable {
    	private Map<String, String> codonToAA = new HashMap<String, String> ();

    	private CodonTable(Map<String, String> codonToAA) {
    		this.codonToAA = Collections.unmodifiableMap(codonToAA);
    	};
    	
    	public String toAA(String codon) {
    		return codonToAA.get(codon.toUpperCase());
    	}

    	public static CodonTable buildCodonTable(String inp) {
    		Map<String, String> codonToAA = new HashMap<String, String> ();
    		for (String line: inp.split("\n")) {
    			if (StringUtils.strip(line).length()==0) {
    				continue;
    			}
    			if (line.charAt(0) == '#') {
    				continue;
    			}
    			// only the first two columns matter:
    			// CODON AA
    			String[] spl = line.split(" ");
    			codonToAA.put(spl[0].toUpperCase(),spl[1]);
    		}
    		
    		return new CodonTable(codonToAA);
    	}
    	public static final CodonTable DEFAULT = buildCodonTable(
    			"TTT F Phe\n"
    			+ "TCT S Ser\n"
    			+ "TAT Y Tyr\n"
    			+ "TGT C Cys\n"
    			+ "TTC F Phe\n"
    			+ "TCC S Ser\n"
    			+ "TAC Y Tyr\n"
    			+ "TGC C Cys\n"
    			+ "TTA L Leu\n"
    			+ "TCA S Ser\n"
    			+ "TAA * Ter\n"
    			+ "TGA * Ter\n"
    			+ "TTG L Leu\n"
    			+ "TCG S Ser\n"
    			+ "TAG * Ter\n"
    			+ "TGG W Trp\n"
    			+ "CTT L Leu\n"
    			+ "CCT P Pro\n"
    			+ "CAT H His\n"
    			+ "CGT R Arg\n"
    			+ "CTC L Leu\n"
    			+ "CCC P Pro\n"
    			+ "CAC H His\n"
    			+ "CGC R Arg\n"
    			+ "CTA L Leu\n"
    			+ "CCA P Pro\n"
    			+ "CAA Q Gln\n"
    			+ "CGA R Arg\n"
    			+ "CTG L Leu\n"
    			+ "CCG P Pro\n"
    			+ "CAG Q Gln\n"
    			+ "CGG R Arg\n"
    			+ "ATT I Ile\n"
    			+ "ACT T Thr\n"
    			+ "AAT N Asn\n"
    			+ "AGT S Ser\n"
    			+ "ATC I Ile\n"
    			+ "ACC T Thr\n"
    			+ "AAC N Asn\n"
    			+ "AGC S Ser\n"
    			+ "ATA I Ile\n"
    			+ "ACA T Thr\n"
    			+ "AAA K Lys\n"
    			+ "AGA R Arg\n"
    			+ "ATG M Met\n"
    			+ "ACG T Thr\n"
    			+ "AAG K Lys\n"
    			+ "AGG R Arg\n"
    			+ "GTT V Val\n"
    			+ "GCT A Ala\n"
    			+ "GAT D Asp\n"
    			+ "GGT G Gly\n"
    			+ "GTC V Val\n"
    			+ "GCC A Ala\n"
    			+ "GAC D Asp\n"
    			+ "GGC G Gly\n"
    			+ "GTA V Val\n"
    			+ "GCA A Ala\n"
    			+ "GAA E Glu\n"
    			+ "GGA G Gly\n"
    			+ "GTG V Val\n"
    			+ "GCG A Ala\n"
    			+ "GAG E Glu\n"
    			+ "GGG G Gly  ");
    	public static final CodonTable MITOCHONDRIAL = buildCodonTable(
    			"TTT F Phe\n"
    			+ "TCT S Ser\n"
    			+ "TAT Y Tyr\n"
    			+ "TGT C Cys\n"
    			+ "TTC F Phe\n"
    			+ "TCC S Ser\n"
    			+ "TAC Y Tyr\n"
    			+ "TGC C Cys\n"
    			+ "TTA L Leu\n"
    			+ "TCA S Ser\n"
    			+ "TAA * Ter\n"
    			+ "TGA W Trp\n"
    			+ "TTG L Leu\n"
    			+ "TCG S Ser\n"
    			+ "TAG * Ter\n"
    			+ "TGG W Trp\n"
    			+ "CTT L Leu\n"
    			+ "CCT P Pro\n"
    			+ "CAT H His\n"
    			+ "CGT R Arg\n"
    			+ "CTC L Leu\n"
    			+ "CCC P Pro\n"
    			+ "CAC H His\n"
    			+ "CGC R Arg\n"
    			+ "CTA L Leu\n"
    			+ "CCA P Pro\n"
    			+ "CAA Q Gln\n"
    			+ "CGA R Arg\n"
    			+ "CTG L Leu\n"
    			+ "CCG P Pro\n"
    			+ "CAG Q Gln\n"
    			+ "CGG R Arg\n"
    			+ "ATT I Ile\n"
    			+ "ACT T Thr\n"
    			+ "AAT N Asn\n"
    			+ "AGT S Ser\n"
    			+ "ATC I Ile\n"
    			+ "ACC T Thr\n"
    			+ "AAC N Asn\n"
    			+ "AGC S Ser\n"
    			+ "ATA M Met\n"
    			+ "ACA T Thr\n"
    			+ "AAA K Lys\n"
    			+ "AGA * Ter\n"
    			+ "ATG M Met\n"
    			+ "ACG T Thr\n"
    			+ "AAG K Lys\n"
    			+ "AGG * Ter\n"
    			+ "GTT V Val\n"
    			+ "GCT A Ala\n"
    			+ "GAT D Asp\n"
    			+ "GGT G Gly\n"
    			+ "GTC V Val\n"
    			+ "GCC A Ala\n"
    			+ "GAC D Asp\n"
    			+ "GGC G Gly\n"
    			+ "GTA V Val\n"
    			+ "GCA A Ala\n"
    			+ "GAA E Glu\n"
    			+ "GGA G Gly\n"
    			+ "GTG V Val\n"
    			+ "GCG A Ala\n"
    			+ "GAG E Glu\n"
    			+ "GGG G Gly");
    }
	public static String revcomp(String seq) {
        String out = "";
        for (int i=seq.length(); i > 0; i--) {
            char base = seq.charAt(i-1);
            
            switch (base) {
            case 'A':
                out += 'T';
                break;
            case 'a':
                out += 't';
                break;
            case 'C':
                out += 'G';
                break;
            case 'c':
                out += 'g';
                break;
            case 'G':
                out += 'C';
                break;
            case 'g':
                out += 'c';
                break;
            case 'T':
                out += 'A';
                break;
            case 't':
                out += 'a';
                break;
            case 'N':
                out += 'N';
                break;
            case 'n':
                out += 'n';
                break;
            case '.':
                out += '.';
                break;
            case '-':
                out += '-';
                break;
            case 'U':
                out += 'A';
                break;
            case 'u':
                out += 'a';
            case 'R':
                out += 'Y';
                break;
            case 'r':
                out += 'y';
                break;
            case 'Y':
                out += 'R';
                break;
            case 'y':
                out += 'r';
            case 'S':
                out += 'S';
                break;
            case 's':
                out += 's';
            case 'W':
                out += 'W';
                break;
            case 'w':
                out += 'w';
            case 'K':
                out += 'M';
                break;
            case 'k':
                out += 'm';
            case 'M':
                out += 'K';
                break;
            case 'm':
                out += 'k';
            case 'B':
                out += 'V';
                break;
            case 'b':
                out += 'v';
            case 'D':
                out += 'H';
                break;
            case 'd':
                out += 'h';
            case 'H':
                out += 'D';
                break;
            case 'h':
                out += 'd';
            case 'V':
                out += 'B';
                break;
            case 'v':
                out += 'b';
            default:
                out += base;
            }
        }
        
        return out;
    }

    public static String compliment(String seq) {
        String out = "";
        for (int i=0; i < seq.length(); i++) {
            char base = seq.charAt(i);
            
            switch (base) {
            case 'A':
                out += 'T';
                break;
            case 'a':
                out += 't';
                break;
            case 'C':
                out += 'G';
                break;
            case 'c':
                out += 'g';
                break;
            case 'G':
                out += 'C';
                break;
            case 'g':
                out += 'c';
                break;
            case 'T':
                out += 'A';
                break;
            case 't':
                out += 'a';
                break;
            case 'N':
                out += 'N';
                break;
            case 'n':
                out += 'n';
                break;
            case '.':
                out += '.';
                break;
            case '-':
                out += '-';
                break;
            case 'U':
                out += 'A';
                break;
            case 'u':
                out += 'a';
            case 'R':
                out += 'Y';
                break;
            case 'r':
                out += 'y';
                break;
            case 'Y':
                out += 'R';
                break;
            case 'y':
                out += 'r';
            case 'S':
                out += 'S';
                break;
            case 's':
                out += 's';
            case 'W':
                out += 'W';
                break;
            case 'w':
                out += 'w';
            case 'K':
                out += 'M';
                break;
            case 'k':
                out += 'm';
            case 'M':
                out += 'K';
                break;
            case 'm':
                out += 'k';
            case 'B':
                out += 'V';
                break;
            case 'b':
                out += 'v';
            case 'D':
                out += 'H';
                break;
            case 'd':
                out += 'h';
            case 'H':
                out += 'D';
                break;
            case 'h':
                out += 'd';
            case 'V':
                out += 'B';
                break;
            case 'v':
                out += 'b';
            default:
                out += base;
            }
        }
        
        return out;
    }
    
    /**
     * Generates a random DNA sequence
     * 
     * By default uses human DNA frequencies ACGT: 0.2, 0.3, 0.3, 0.2 
     * @param len
     * @return
     */
    public static String generateRandomSeq(int len) {
    	return generateRandomSeq(len, new Random());
    }

    public static String generateRandomSeq(int len, Random rand) {
    	return generateRandomSeq(len, new char[] {'A','C','G','T'}, new double[] {0.2,0.3,0.3,0.2}, rand);
    }

    public static String generateRandomSeq(int len, char[] alpha, double[] bg) {
    	return generateRandomSeq(len, alpha, bg, new Random());
    }        
    public static String generateRandomSeq(int len, char[] alpha, double[] bg, Random rand) {
        	String s = "";
    	if (rand == null) {
    		rand = new Random();
    	}
    	double[] thres = new double[bg.length+1];
    	double acc = 0;
    	for (int i=0; i< bg.length; i++) {
    		thres[i] = acc;
    		acc += bg[i];
    	}
    	thres[bg.length] = 1;
    	
//    	System.out.println(StringUtils.join(";", thres));
    	
    	while (s.length() < len) {
    		double val = rand.nextDouble();
//    		System.out.println(val);
        	for (int i=0; i< thres.length-1; i++) {
        		if (val > thres[i] && val < thres[i+1]) {
        			s += alpha[i];
        		}
        	}
    	}
    	
    	return s;
    }

    public static String translate(String seq, boolean keepCase) {
    	return translate(seq, SeqUtils.CodonTable.DEFAULT, keepCase, true);
    }

    public static String translate(String seq, boolean keepCase, boolean forceFirstMet) {
    	return translate(seq, SeqUtils.CodonTable.DEFAULT, keepCase,forceFirstMet);
    }
    public static String translate(String seq, CodonTable table, boolean keepCase) {
    	return translate(seq, table, keepCase, true);
    }
    /**
     * Translate a cDNA sequence to a peptide. Translation will stop at the first stop codon (which will not be included in the result)
     * 
     * @param seq cDNA sequence -- needs to be in codon triplets
     * @param table Codon Table (default uses Vertebrate)
     * @param keepCase if the input codon is lowercase, output a lower case amino acid
     * @param forceFirstMet The firsrt AA should always be a methionine, regardless of if the start codon is ATG
     * @return amino acid sequence
     */
    public static String translate(String seq, CodonTable table, boolean keepCase, boolean forceFirstMet) {
    	String peptide = "";
    	for (int i=0; i+2 < seq.length(); i+=3) {
    		String codon = seq.substring(i, i+3);
    		String aa = table.toAA(codon);
    		if (aa == null) {
    			break;
    		} 

    		if (peptide.length() == 0 && !codon.toUpperCase().equals("ATG") && forceFirstMet) {
    			// non-ATG starts still end up with a first AA as methionine.
    			aa = "M";
    		}
    		
    		if (keepCase) {
    			if (Character.isLowerCase(codon.charAt(0)) || Character.isLowerCase(codon.charAt(1)) || Character.isLowerCase(codon.charAt(2))) {
    				peptide += aa.toLowerCase();
    			} else {
        			peptide += aa;
    			}
    		} else {
    			peptide += aa;
    		}

			if (aa.equals("*")) {
				break;
			}

    	}
    	
    	return peptide;
    }

	public static String ambiguousNucleotide(String bases) {
		int acc = 0;
		
		bases = bases.toUpperCase();

		for (int i=0; i<bases.length(); i++) {
			if (bases.charAt(i) == 'A') {        // A              1
				acc = acc | 0x1;
			} else if (bases.charAt(i) == 'C') { // C              2
				acc = acc | 0x2;
			} else if (bases.charAt(i) == 'M') { // A or C         1 + 2
				acc = acc | 0x3;
			} else if (bases.charAt(i) == 'G') { // G              4
				acc = acc | 0x4;
			} else if (bases.charAt(i) == 'R') { // A or G         1 + 4
				acc = acc | 0x5;
			} else if (bases.charAt(i) == 'S') { // C or G         2 + 4
				acc = acc | 0x6;
			} else if (bases.charAt(i) == 'M') { // A or C or G    1 + 2 + 4
				acc = acc | 0x7;
			} else if (bases.charAt(i) == 'T') { // T              8
				acc = acc | 0x8;
			} else if (bases.charAt(i) == 'W') { // A or T         1 + 8
				acc = acc | 0x9;
			} else if (bases.charAt(i) == 'Y') { // C or T         2 + 8
				acc = acc | 0xA;
			} else if (bases.charAt(i) == 'H') { // A or C or T    1 + 2 + 8
				acc = acc | 0xB;
			} else if (bases.charAt(i) == 'K') { // G or T         4 + 8
				acc = acc | 0xC;
			} else if (bases.charAt(i) == 'D') { // A or G or T    1 + 4 + 8
				acc = acc | 0xD;
			} else if (bases.charAt(i) == 'B') { // C or G or T    2 + 4 + 8
				acc = acc | 0xE;
			} else {
				return "N"; // unknown... 1 + 2 + 4 + 8
			}
		}

		if (acc == 0) {
			return "";
		}
		
		String ambiguous = ".ACMGRSMTWYHKDBN"; 
		
		
		return ""+ambiguous.charAt(acc);
	}
 
	/**
	 * Determine if two bases match each other -- allowing for ambiguities.  MUST BE UPPER CASE
	 * @param query
	 * @param ref
	 * @return
	 */
    public static boolean nucleotideMatch(char one, char two) {
    	if (one == two) {
    		return true;
    	}

		String ambiguous = ".ACMGRSMTWYHKDBN";
		int oneIdx = ambiguous.indexOf(one);
		int twoIdx = ambiguous.indexOf(two);
		
		if (oneIdx == -1 || twoIdx == -1) {
			return false;
		}
		
		return (oneIdx & twoIdx) > 0;
    }

}
