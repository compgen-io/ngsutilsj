package io.compgen.ngsutils.support;

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
}
