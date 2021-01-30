package io.compgen.ngsutils.support;

import java.util.Random;

public class SeqUtils {
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

}
