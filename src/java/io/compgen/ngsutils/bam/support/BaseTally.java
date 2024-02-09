package io.compgen.ngsutils.bam.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;

public class BaseTally {
    public class BaseCount implements Comparable<BaseCount> {
        private String base;
        private int count=0;
        private int plus=0;
        private int minus=0;
        
        private BaseCount(String base) {
            this.base = base;
        }
        
        @Override
        public int compareTo(BaseCount o) {
            return Integer.compare(count, o.count);
        }
        
        private void incr(boolean plusStrand) {
            count++;
            if (plusStrand) {
                plus++;
            } else {
            	minus++;
            }
        }
        
        public String getBase() {
            return base;
        }
        
        public int getCount() {
            return count;
        }
        public int getPlus() {
            return plus;
        }
        public int getMinus() {
            return minus;
        }
    }
    
    private Map<String, BaseCount> tallies = new HashMap<String, BaseCount>();
    private int wildCount = 0;
    
    public BaseTally(String... bases) {
        for (String base: bases) {
            tallies.put(base, new BaseCount(base));
        }
    }
    
    public void incr(String base, boolean plusStrand) {
    	if (!tallies.containsKey(base)) {
            tallies.put(base, new BaseCount(base));
    	}
        tallies.get(base).incr(plusStrand);
    }
    
    public void incrWild() {
    	this.wildCount++;
    }

    public int getWildCount() {
    	return this.wildCount;
    }
    
    public List<BaseCount> getSorted() {
        List<BaseCount> l = new ArrayList<BaseCount>(tallies.values());
        Collections.sort(l);
        return l;
    }

    public boolean contains(String base) {
        return tallies.containsKey(base);
    }

    public int getCount(String base) {
    	if (tallies.containsKey(base)) {
    		return tallies.get(base).getCount();
    	}
    	return 0;
    }

    
    public int getPlusCount(String base) {
    	if (tallies.containsKey(base)) {
    		return tallies.get(base).getPlus();
    	}
    	return 0;
    }

    
    public int getMinusCount(String base) {
    	if (tallies.containsKey(base)) {
    		return tallies.get(base).getMinus();
    	}
    	return 0;
    }

    
    public int sum() {
        return nonRefSum(null);
    }
    public int nonRefSum(String refBase) {
        int acc = 0;
        for (String base: tallies.keySet()) {
            if (refBase==null || !base.equals(refBase)) {
                acc += tallies.get(base).getCount();
            }
        }
        return acc;
    }

	public static BaseTally parsePileupRecord(List<PileupBaseCall> calls) {

        BaseTally bt = new BaseTally();
        
        for (PileupBaseCall call: calls) {
            if (call.op == PileupRecord.PileupBaseCallOp.Match) {
                if (bt.contains(call.call)) {
                    bt.incr(call.call, call.plusStrand);
                } else {
                	bt.incrWild();
                }
            } else if (call.op == PileupRecord.PileupBaseCallOp.Ins){
                bt.incr("+" + call.call, call.plusStrand);
            } else if (call.op == PileupRecord.PileupBaseCallOp.Del){
                bt.incr("-" + call.call, call.plusStrand);
            }

        }

        return bt;
	}

	/**
	 * return the total depth
	 * @return
	 */
	public int calcDepth() {
		int acc = 0;
		for (String base: this.tallies.keySet()) {
			acc += tallies.get(base).count;			
		}
		return acc;
	}   

	/**
	 * return the depth for each base, in order: refbase:alt1:alt2...
	 * @param refBase
	 * @param alts
	 * @return
	 */
	public int[] calcAltDepth(String refBase, List<String> alts) {
		int[] ret = new int[alts.size()+1];
		ret[0] = getCount(refBase);
		for (int i=0; i<alts.size(); i++) {
			ret[i+1] = getCount(alts.get(i));			
		}
		return ret;
	}

	/**
	 * return the allele frequency for each base, in order: refbase:alt1:alt2...
	 * @param refBase
	 * @param alts
	 * @return
	 */
	public double[] calcAltFreq(List<String> alts) {
		double total = this.calcDepth();
		
		double[] ret = new double[alts.size()];
		for (int i=0; i<alts.size(); i++) {
			ret[i] = getCount(alts.get(i)) / total;			
		}
		return ret;
	}

	/**
	 * return the depth for each base-strand, in order: ref+:ref-:alt1+:alt1-:alt2+...
	 * @param refBase
	 * @param alts
	 * @return
	 */
	public int[] calcAltStrand(String refBase, List<String> alts) {
		int[] ret = new int[2*(alts.size()+1)];
		
		ret[0] = getPlusCount(refBase);
		ret[1] = getMinusCount(refBase);

		for (int i=0; i<alts.size(); i++) {
			ret[(2*(i+1))] = getPlusCount(alts.get(i));			
			ret[(2*(i+1))+1] = getMinusCount(alts.get(i));			
		}
		return ret;
	}
	
}
