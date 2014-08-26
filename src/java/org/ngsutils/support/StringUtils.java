package org.ngsutils.support;

import java.util.ArrayList;
import java.util.List;
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
    
    public static String strip(String str, String rem) {
        Pattern pattern = Pattern.compile("^"+ rem +"(.*?)"+ rem +"$");
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
    }
    public static String strip(String str) {
        return strip(str, "\\s*");
    }
    
    public static String rstrip(String str) {
        Pattern pattern = Pattern.compile("^(.*?)\\s*$");        
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
    }
    
    public static String lstrip(String str) {
        Pattern pattern = Pattern.compile("^\\s*(.*?)$");
        Matcher m = pattern.matcher(str);
        m.find();
        return m.group(1);
    }
    
    public static int matchCount(String one, String two) {
        int matches = 0;
        for (int i=0; i < one.length()  && i < two.length(); i++) {
            if (one.charAt(i) == two.charAt(i)) {
                matches++;
            }
        }
        return matches;
    }
    
    public static String join(String delim, String[] args) {
        String out = "";
        
        for (String arg: args) {
            if (out.equals("")) {
                out = arg;
            } else {
                out = out + delim + arg;
            }
        }
        
        return out;
    }

    public static String join(String delim, double[] args) {
        String out = "";
        
        for (Number arg: args) {
            if (out.equals("")) {
                out = ""+arg;
            } else {
                out = out + delim + arg;
            }
        }
        
        return out;
    }

    public static String join(String delim, int[] args) {
        String out = "";
        
        for (Number arg: args) {
            if (out.equals("")) {
                out = ""+arg;
            } else {
                out = out + delim + arg;
            }
        }
        
        return out;
    }
    
    public static String join(String delim, Iterable<? extends Object> args) {
        String out = "";
        
        for (Object arg: args) {
            if (out.equals("")) {
                out = arg.toString();
            } else {
                out = out + delim + arg.toString();
            }
        }
        
        return out;
    }
    
    public static List<String> getUniqueNames(List<String> strs) {
        List<String> prefixFiltered = filterCommonFragments(strs);
        List<String> reversed = new ArrayList<String>();
        for (String s:prefixFiltered) {
            reversed.add(new StringBuilder(s).reverse().toString());
        }
        List<String> suffixFiltered = filterCommonFragments(reversed);
        List<String> uniqueNames = new ArrayList<String>();
        for (String s:suffixFiltered) {
            uniqueNames.add(new StringBuilder(s).reverse().toString());
        }
        
        return uniqueNames;
    }
    
    public static List<String> filterCommonFragments(List<String> strs) {
        List<String[]> splits = new ArrayList<String[]>();
        int fragments = 0;
        for (String s: strs) {
            String[] spl = s.split("\\W"); // split all non-word characters
            splits.add(spl);
            fragments = Math.max(fragments,  spl.length);
        }

        int i=0;
        while (i < fragments) {
            boolean same = true;
            for (int j=1; j<splits.size(); j++) {
                if (i >= splits.get(j).length || !splits.get(j)[i].equals(splits.get(0)[i])) {
                    same = false;
                    break;
                }
            }
            if (!same) {
                break;
            }
            i++;
        }
        
        List<String> out = new ArrayList<String>();
        for (String[] spl: splits) {
            List<String> tmp = new ArrayList<String>();
            for (int j=0; j<fragments; j++) {
                if (j >= i && j < spl.length) {
                    tmp.add(spl[j]);
                }
            }
            out.add(StringUtils.join(".", tmp));
        }
        return out;
        
    }
    public static String join(String delim, Object[] vals) {
        if (vals == null || vals.length == 0) {
            return "";
        }
        String[] out = new String[vals.length];
        for (int i=0; i<vals.length; i++) {
            if (vals[i] == null) {
                out[i] = "";
            } else {
                out[i] = vals[i].toString();
            }
        }
        return join(delim, out);
    }
    public static String reverse(String str) {
        String out = "";
        for (int i=str.length(); i > 0; i--) {
            out += str.charAt(i-1);
        }
        return out;
    }
}
    