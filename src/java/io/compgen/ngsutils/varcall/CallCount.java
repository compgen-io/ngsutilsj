package io.compgen.ngsutils.varcall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class CallCount implements Comparable<CallCount>{
    protected static class CallCounter {
        private Map<String, CallCount> counts = new HashMap<String,CallCount>();
        
        public void addBase(char base, boolean plusStrand) {
            addBase(Character.toString(base), plusStrand);
        }
        public void addBase(String base, boolean plusStrand) {
            base = base.toUpperCase();
            if (!counts.containsKey(base)) {
                counts.put(base, new CallCount(base));
            }

            if (plusStrand) {
                counts.get(base).incrPlus();
            } else {
                counts.get(base).incrMinus();
            }
        }
        
        public List<CallCount> getCounts(int minCallDepth) {
            List<CallCount> out = new ArrayList<CallCount>();
            for (CallCount cc: counts.values()) {
                if (cc.getCount() >= minCallDepth) {
                    out.add(cc);
                }
            }
            return out;
        }
    }

    public static List<CallCount> parsePileupString(String callStr, String qualStr, String refBase, int minBaseQual, int minCallDepth, boolean debug) {
        CallCounter counter = new CallCounter();
        refBase = refBase.toUpperCase();
        int qualpos = 0;
        
        if (debug) {
            System.out.println(callStr+"\t"+qualStr);
        }
        
        String debugStr="";
        
        for (int i=0; i<callStr.length(); i++) {
            char base = callStr.charAt(i);
            
            debugStr += i+":"+ base+" (qualidx:"+qualpos+")\n";
            
            if (base == '^') {
                // start of read, next char will be mapping qual
                i++;
                continue;
            } else if (base == '$') {
                // end of read
                continue;
            } else if (base == '*') {
                // previously deleted
                continue;
            } else if (base == '+' || base == '-') {
                // indel
                String buf = "";
                i++;
                while ("0123456789".indexOf(callStr.charAt(i)) > -1) {
                    buf += callStr.charAt(i);
                    i++;
                }

                int indelSize = Integer.parseInt(buf);
                String indelSeq = base+callStr.substring(i, i+indelSize);
                if (debug) {
                    System.out.println("Adding: "+indelSeq);
                }
                counter.addBase(indelSeq, Character.isUpperCase(indelSeq.charAt(1)));
                i+= indelSize - 1;

                continue;
            }
            
            // potential SNVs
            if (qualpos >= qualStr.length()) {
                System.out.println("Error processing pileup: "+callStr+"\t"+qualStr);
                System.out.println(debugStr);
            }
            int qual = (int) qualStr.charAt(qualpos) - 33;
            if (debug) {
                System.out.println("Base: "+base +" / qual: "+qual);
            }
            if (qual >= minBaseQual) {
                if (base == '.') {
                    counter.addBase(refBase, true);
                } else if (base == ',') {
                    counter.addBase(refBase, false);
                } else if (Character.isUpperCase(base)) {
                    counter.addBase(base, true);
                } else {
                    counter.addBase(base, false);
                }
            }
                
            qualpos++;
        }
        
        return counter.getCounts(minCallDepth);
        
    }
    // Indels should be +AAA or -AAA (where AAA is the sequence inserted or removed)
    private String call;
    private int countPlus=0;
    private int countMinus=0;

    protected CallCount(String call) {
        this.call = call.toUpperCase();
    }
    
    protected void incrPlus() {
        this.countPlus++;
    }
    
    protected void incrMinus() {
        this.countMinus++;
    }

    public String getCall() {
        return call;
    }
    
    public int getCountPlus() {
        return countPlus;
    }
    
    public int getCountMinus() {
        return countMinus;
    }
    
    public int getCount() {
        return countPlus + countMinus;
    }
    
    public boolean isInsert() {
        return call.charAt(0) == '+';
    }
    
    public boolean isDeletion() {
        return call.charAt(0) == '-';
    }
    
    @Override
    public int compareTo(CallCount o) {
        return getCount() - o.getCount();
    }

    public int getMinorStrandCount() {
        return (countPlus < countMinus) ? countPlus : countMinus;
    }
    
    public String toString() {
        return getCall() + "("+getCount()+")";
    }
}
