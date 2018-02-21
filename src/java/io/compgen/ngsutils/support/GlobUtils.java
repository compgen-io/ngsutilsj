package io.compgen.ngsutils.support;

import java.util.regex.Pattern;

public class GlobUtils {
    public static String globToRegex(String glob) {
        String out = "^";
        
        for (int i=0; i<glob.length(); i++) {
            char c = glob.charAt(i);
            switch(c) {
            case '*':
                if (out.charAt(out.length()-1) == '\\') {
                    out = out.substring(0,  out.length()-1) + "*";
                } else {
                    out += ".*";
                }
                break;
            case '?':
                if (out.charAt(out.length()-1) == '\\') {
                    out = out.substring(0,  out.length()-1) + "?";
                } else {
                    out += ".?";
                }
                break;
            case '.':
            case '(':
            case ')':
            case '+':
            case '|':
            case '^':
            case '$':
            case '@':
            case '%':
                out += "\\" + c;
                break;
            case '\\':
                out += "\\\\";
                break;
            default:
                out += c;
                break;
            }
        }
        
        return out+"$";
    }
    
    public static boolean matches(String query, String glob) {
//        System.err.println("Matching: "+query+ " to "+glob + " regex:"+globToRegex(glob));

        return Pattern.matches(globToRegex(glob), query);
    }

}
