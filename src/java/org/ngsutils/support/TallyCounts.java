package org.ngsutils.support;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class TallyCounts {
    private Map<Integer, Integer> map = new HashMap<Integer, Integer>();

    private int min = -1;
    private int max = -1;
    
    private boolean inclusive = false;
    
    public TallyCounts() {}
    public TallyCounts(boolean inclusive) {
        this.inclusive = inclusive;
    }
    
    
    public void incr(int k) {
        if (inclusive) {
            for (int i=1; i<=k; i++) {
                incrementKey(i);
            }
        } else {
            incrementKey(k);
        }
    }
	
    private void incrementKey(int k) {
        if (!map.containsKey(k)) {
            if (min == -1 || k < min) {
                min = k;
            }
            if (max == -1 || k > max) {
                max = k;
            }
            map.put(k, 0);
        }
        map.put(k, map.get(k) + 1);
    }
    public int getMin(){
        return min;
    }
    public int getMax(){
        return max;
    }
    public int getCount(int k) {
        if (map.containsKey(k)) {
            return map.get(k);
        }
        return 0;
    }

    public double getTotal() {
        int count = 0;
        for (int i=min; i<=max; i++) {
            if (map.containsKey(i)) {
                count += map.get(i);
            }
        }
        return count;
    }

    public double getMean() {
        int acc = 0;
        int count = 0;
        for (int i=min; i<=max; i++) {
            if (map.containsKey(i)) {
                acc += i * map.get(i);
                count += map.get(i);
            }
        }
        return (double) acc / count;
    }
    
    public void write(OutputStream out) throws IOException {
        for (int i=min; i<=max; i++) {
            String str = "";
            if (map.containsKey(i)) {
                str = "" + i + "\t" + map.get(i)+"\n";
            } else {
                str = "" + i + "\t0\n";
            }
            out.write(str.getBytes());
        }        
    }
}
