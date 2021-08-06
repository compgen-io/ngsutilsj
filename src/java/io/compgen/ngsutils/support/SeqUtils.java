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
    		return codonToAA.get(codon);
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
    			codonToAA.put(spl[0],spl[1]);
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
    			+ "TAA X Ter\n"
    			+ "TGA X Ter\n"
    			+ "TTG L Leu\n"
    			+ "TCG S Ser\n"
    			+ "TAG X Ter\n"
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
    			+ "TAG X Ter\n"
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
    			+ "AGA X Ter\n"
    			+ "ATG M Met\n"
    			+ "ACG T Thr\n"
    			+ "AAG K Lys\n"
    			+ "AGG X Ter\n"
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

    public static String translate(String seq) {
    	return translate(seq, SeqUtils.CodonTable.DEFAULT);
    }
    /**
     * Translate a cDNA sequence to a peptide. Translation will stop at the first stop codon (which will not be included in the result)
     * 
     * @param seq cDNA sequence -- needs to be in codon triplets
     * @param table Codon Table (default uses Vertebrate)
     * @return amino acid sequence
     */
    public static String translate(String seq, CodonTable table) {
    	String peptide = "";
    	for (int i=0; i+2 < seq.length(); i+=3) {
    		String aa = table.toAA(seq.substring(i, i+3));
    		if (aa == null) {
    			break;
    		} 

    		peptide += aa;

			if (aa.equals("X")) {
				break;
			}

    	}
    	
    	return peptide;
    }
 
    
}
