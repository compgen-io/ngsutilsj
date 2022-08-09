package io.compgen.ngsutils.bam.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.compgen.ngsutils.bam.Strand;

public class BaseTally {
    public class BaseCount implements Comparable<BaseCount> {
        private String base;
        private int plusCount = 0;
        private int minusCount = 0;
        private int noneCount = 0;
        
        private BaseCount(String base) {
            this.base = base;
        }
        
        @Override
        public int compareTo(BaseCount o) {
            return Integer.compare(plusCount+minusCount+noneCount, o.plusCount + o.minusCount + o.noneCount);
        }
        
        private void incr(Strand strand) {
        	switch(strand) {
        	case PLUS:
        		plusCount++;
        		break;
        	case MINUS:
        		minusCount++;
        		break;
        	default:
        		noneCount++;
        		break;
        		
        	}
        }
        
        public String getBase() {
            return base;
        }
        
        public int getCount() {
            return plusCount+minusCount+noneCount;
        }
        public int getStrandCount(Strand strand) {
            if (strand == Strand.PLUS) {
            	return plusCount;
            } else if (strand == Strand.MINUS) {
            	return minusCount;
            }
            return noneCount;
        }
    }
    
    private Map<String, BaseCount> tallies = new HashMap<String, BaseCount>();
    
    public BaseTally(String... bases) {
        for (String base: bases) {
            tallies.put(base, new BaseCount(base));
        }
    }

    public void incr(String base) {
    	if (!tallies.containsKey(base)) {
            tallies.put(base, new BaseCount(base));
    	}
		tallies.get(base).incr(Strand.NONE);
    }
    
    public void incrStrand(String base, boolean isPlusStrand) {
    	if (!tallies.containsKey(base)) {
            tallies.put(base, new BaseCount(base));
    	}
    	if (isPlusStrand) {
    		tallies.get(base).incr(Strand.PLUS);
    	} else {
    		tallies.get(base).incr(Strand.MINUS);
    	}
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
        return tallies.get(base).getCount();        
    }

    public int getStrandCount(String base, Strand strand) {
        return tallies.get(base).getStrandCount(strand);        
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
   
}
