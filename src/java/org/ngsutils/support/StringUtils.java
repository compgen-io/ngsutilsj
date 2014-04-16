package org.ngsutils.support;

public class StringUtils {
    public static String slice(String str, int start, int end) {
        return str.substring(start, end);
    }
    public static String sliceRight(String str, int end) {
        if (end > 0) {
            return str;
        }
        return str.substring(str.length()+end);
    }
    
    public static int matchCount(String one, String two) {
        int matches = 0;
        for (int i=0; i < one.length()  && i < two.length(); i++) {
//            System.err.println("testing: ["+ i +"] "+one.charAt(i) + " = " + two.charAt(i));
            if (one.charAt(i) == two.charAt(i)) {
                matches++;
            }
        }
        return matches;
    }
}
