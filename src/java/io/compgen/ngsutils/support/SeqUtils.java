package io.compgen.ngsutils.support;

public class SeqUtils {
    public static String revcomp(String seq) {
        String out = "";
        for (int i=seq.length()-1; i > -1; i--) {
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
            default:
                out += base;
            }
            
        }
        
        return out;
    }
}
