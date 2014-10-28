package org.ngsutils.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class NaturalSort {
    private class NaturalToken implements Comparable<NaturalToken>{
        public final boolean digits;
        public final int intVal;
        public final String strVal;
        
        private NaturalToken(int val) {
            digits = true;
            intVal = val;
            strVal = null;
        }

        private NaturalToken(String val) {
            digits = false;
            intVal = -1;
            strVal = val;
        }

        @Override
        public int compareTo(NaturalToken o) {
            if (o == null) {
                return 1;
            }
            if (this.digits && !o.digits) { 
                return -1;
            }
            if (!this.digits && o.digits) {
                return 1;
            }
            
            if (this.digits) {
                return this.intVal - o.intVal;
            }
            
            return this.strVal.compareTo(o.strVal);
        }
        
        public String toString() {
            if (this.digits) {
                return Integer.toString(intVal);
            }
            return strVal;
        }
    }
    private class CompareVal {
        private int val = 0;
    }
    private class NaturalTokenList implements Comparable<NaturalTokenList>, Iterable<NaturalToken> {

        private List<NaturalToken> tokens = new ArrayList<NaturalToken>();
        
        public void addToken(NaturalToken token) {
            tokens.add(token);
        }
        
        @Override
        public int compareTo(NaturalTokenList o) {
            final CompareVal compareVal = new CompareVal();
            IterUtils.zip(this, o, new IterUtils.Each<NaturalToken, NaturalToken>() {
                @Override
                public void each(NaturalToken foo, NaturalToken bar) {
                    if (compareVal.val == 0) {
                        compareVal.val = foo.compareTo(bar);
                    }
                }
            }, true);
            return compareVal.val;
        }

        public String toString() {
            String s = "";
            for (NaturalToken token:tokens) {
                s += token.toString();
            }
            return s;
        }
        
        @Override
        public Iterator<NaturalToken> iterator() {
            return tokens.iterator();
        }
        
    }
    
    private List<NaturalTokenList> tokenLists = new ArrayList<NaturalTokenList>();
    
    private NaturalSort() {
        
    }
    
    private void addString(String str) {
        NaturalTokenList tokens = new NaturalTokenList();
        String tmp = "";
        boolean digits = false;
        for (int i=0; i<str.length(); i++) {
            if ("0123456789".indexOf(str.charAt(i)) > -1) {
                if (!digits) {
                    if (!tmp.equals("")) {
                        tokens.addToken(new NaturalToken(tmp));
                        tmp = "";
                    }
                }
                digits = true;
                tmp+=str.charAt(i);
            } else {
                if (digits) {
                    tokens.addToken(new NaturalToken(Integer.parseInt(tmp)));
                    tmp = "";
                }
                digits = false;
                tmp += str.charAt(i);
            }
        }
        if (!tmp.equals("")) {
            if (digits) {
                tokens.addToken(new NaturalToken(Integer.parseInt(tmp)));
            } else {
                tokens.addToken(new NaturalToken(tmp));
            }
        }
        tokenLists.add(tokens);
    }

    private List<String> sort() {
        Collections.sort(tokenLists);
        
        List<String> sorted = new ArrayList<String>();
        for (NaturalTokenList ntl: tokenLists) {
            sorted.add(ntl.toString());
        }
        return sorted;
    }


    
    public static List<String> naturalSort(Iterable<String>iter) {
        List<String> vals = new ArrayList<String>();
        for (String s: iter) {
            vals.add(s);
        }

        String common = StringUtils.findCommonPrefix(vals);
                
        NaturalSort sorter = new NaturalSort();
        for (String s: iter) {
            sorter.addString(s.substring(common.length()));
        }
        vals.clear();
        
        for (String s: sorter.sort()) {
            vals.add(common+s);
        }
        return vals;
    }

    public static String[] naturalSort(String[] str) {
        List<String> vals = new ArrayList<String>(str.length);
        for (String s: str) {
            vals.add(s);
        }
        
        List<String> sorted = naturalSort(vals); 
        String[] out = new String[str.length];
        for (int i=0; i<sorted.size(); i++) {
            out[i] = sorted.get(i);
        }
        
        return out;
    }
}
