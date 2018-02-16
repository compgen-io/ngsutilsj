package io.compgen.ngsutils.bam.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseTally {
    public class BaseCount implements Comparable<BaseCount> {
        private String base;
        private int count=0;
        
        private BaseCount(String base) {
            this.base = base;
        }
        
        @Override
        public int compareTo(BaseCount o) {
            return Integer.compare(count, o.count);
        }
        
        private void incr() {
            count++;
        }
        
        public String getBase() {
            return base;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    private Map<String, BaseCount> tallies = new HashMap<String, BaseCount>();
    
    public BaseTally(String... bases) {
        for (String base: bases) {
            tallies.put(base, new BaseCount(base));
        }
    }
    
    public void incr(String base) {
        tallies.get(base).incr();
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
