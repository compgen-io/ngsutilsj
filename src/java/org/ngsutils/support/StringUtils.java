package org.ngsutils.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    public static String strip(String str) {
        Pattern pattern = Pattern.compile("^\\s*(.*)\\s*$");
        
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
    }
    
    public static String rstrip(String str) {
        Pattern pattern = Pattern.compile("^(.*)\\s*$");
        
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
    }
    
    public static String lstrip(String str) {
        Pattern pattern = Pattern.compile("^\\s*(.*)$");
        
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
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
