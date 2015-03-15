package io.compgen.ngsutils.support;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
        if (m.find()) {        
            return m.group(1);
        }
        return str;
    }
    public static String strip(String str) {
        return strip(str, "\\s*");
    }
    
    public static String rstrip(String str) {
        Pattern pattern = Pattern.compile("^(.*?)\\s*$");        
        Matcher m = pattern.matcher(str);
        if (m.find()) {        
            return m.group(1);
        }
        return str;
    }
    
    public static String lstrip(String str) {
        Pattern pattern = Pattern.compile("^\\s*(.*?)$");
        Matcher m = pattern.matcher(str);
        if (m.find()) {        
            return m.group(1);
        }
        return str;
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
    public static String join(String delim, byte[] buf) {
        String[] out = new String[buf.length];
        for (int i=0; i<buf.length; i++) {
            out[i] = String.format("%02X", buf[i]);
        }
        return join(delim, out);
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

    public static String byteToString(byte b) {
        return byteArrayToString(new byte[] {b});
    }
    
    public static String byteArrayToString(byte[] buf) {
        return byteArrayToString(buf, 0, buf.length, -1, null);
    }
    public static String byteArrayToString(byte[] buf, int off, int len) {
        return byteArrayToString(buf, off, len, -1, null);
    }
    public static String byteArrayToString(byte[] buf, int wrap) {
        return byteArrayToString(buf, 0, buf.length, wrap, null);
    }
    public static String byteArrayToString(byte[] buf, String sep) {
        return byteArrayToString(buf, 0, buf.length, -1, sep);
    }
    public static String byteArrayToString(byte[] buf, int off, int len, int wrap, String sep) {
        byte[] b = buf;
        if (off > 0 || len != buf.length) {
            b = Arrays.copyOfRange(buf, off, len-off); 
        }

        String calc = new BigInteger(1, b).toString(16);
        while (calc.length() < (b.length*2)) {
            calc = "0"+calc;
        }
        
        if (wrap == -1 && sep == null) {
            return calc;
        }
        
        String out = "";
        int j = 0;

        for (int i=0; i<calc.length(); i=i+2) {
            out += calc.charAt(i);
            out += calc.charAt(i+1);
        
            if (sep !=null) {
                out += sep;
            }
            j++;
            if (wrap > -1 && j >= wrap) {
                out += "\n";
                j = 0;
            }
        }
        return out;
    }

    public static String findCommonPrefix(String[] vals) {
        List<String> l = new ArrayList<String>();
        for (String s: vals) {
            l.add(s);
        }
        return findCommonPrefix(l);
    }
    
    public static String findCommonPrefix(List<String> vals) {
        String prefix = "";
        for (int i=0; i<vals.get(0).length(); i++) {
            char c = vals.get(0).charAt(i);
            boolean found = true;
            for (String s: vals) {
                if (s.length()<=i || s.charAt(i) != c) {
                    found = false;
                    break;
                }
            }
            if (found) {
                prefix += c;
            }
        }
        return prefix;
    }

    public static List<String> naturalSort(Iterable<String>iter) {
        List<String> vals = new ArrayList<String>();
        for (String s: iter) {
            vals.add(s);
        }
    
        Collections.sort(vals, naturalSorter());
        return vals;
    }

    public static void naturalSort(String[] str) {
        Arrays.sort(str, naturalSorter());
    }
    
    public static Comparator<String> naturalSorter() {
        return new Comparator<String>(){
            @Override
            public int compare(String o1, String o2) {
                if (o1==null && o2==null) {
                    return 0;
                }
                if (o1==null) {
                    return -1;
                }
                if (o2==null) {
                    return 1;
                }

                NaturalTokenList ntl1 = NaturalTokenList.parseString(o1);
                NaturalTokenList ntl2 = NaturalTokenList.parseString(o2);
                return ntl1.compareTo(ntl2);
            }};
    }
    
    public static String rfill(String s, int len) {
        return rfill(s, len, ' ');
    }
    public static String rfill(String s, int len, char fillChar) {
        while (s.length() < len) {
            s += fillChar;
        }
        return s;
    }
    public static String[] quotedSplit(String str, char delim) {
        return quotedSplit(str, delim, '"');
    }
    
    public static String[] quotedSplit(String str, char delim, char quoteChar) {
        String buf = "";
        boolean inquote = false;
        List<String> tokens = new ArrayList<String>();
        
        for (int i=0; i< str.length(); i++) {
            if (inquote) {
                buf += str.charAt(i);
                if (str.charAt(i) == quoteChar) {
                    inquote = false;
                }
            } else if (str.charAt(i) == delim) {
                tokens.add(buf);
                buf = "";
            } else {
                buf += str.charAt(i);
                if (str.charAt(i) == quoteChar) {
                    inquote = true;
                }
            }
        }
                
        return tokens.toArray(new String[tokens.size()]);
    }
}
    
