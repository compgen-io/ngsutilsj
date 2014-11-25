package org.ngsutils.support;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class TallyCounts {
    private Map<Integer, Long> map = new HashMap<Integer, Long>();

    private int min = -1;
    private int max = -1;
    
    private long totalCount = 0;
    
//    private boolean inclusive = false;
    
    public TallyCounts() {}
//    public TallyCounts(boolean inclusive) {
//        this.inclusive = inclusive;
//    }
    
    public void incr(int k) {
//        if (inclusive) {
//            for (int i=0; i<=k; i++) {
//                incrementKey(i);
//            }
//        } else {
            incrementKey(k);
//        }
    }
	
    private void incrementKey(int k) {
        if (k < 0) {
            return;
        }
        totalCount += 1;
        if (!map.containsKey(k)) {
            if (min == -1 || k < min) {
                min = k;
            }
            if (max == -1 || k > max) {
                max = k;
            }
            map.put(k, (long) 1);
        } else {
            map.put(k, map.get(k) + 1);
        }
    }
    public int getMin(){
        return min;
    }
    public int getMax(){
        return max;
    }
    public long getCount(int k) {
        if (map.containsKey(k)) {
            return map.get(k);
        }
        return 0;
    }

    public long getTotal() {
        return totalCount;
    }

    public double getMean() {
        long acc = 0;
        long count = 0;
        for (int i=min; i<=max; i++) {
            long tally = getCount(i);
            acc += (i * tally);
            count += tally;
        }
        return (double) acc / count;
    }

    public int getQuantile(double pct) {
        double thres = pct * totalCount;
        long count = 0;
        
        for (int i=min; i<=max && count < thres; i++) {
            count += getCount(i);
//            System.err.println(pct+"\t"+i+"\t"+count+"\t"+thres);
            if (count > thres) {
                return i;
            }
        }
        
        return max;
    }
    
    public void write(OutputStream out) throws IOException {
//        out.write(("\nMin: "+min+"\n").getBytes());
//        out.write(("Max: "+max+"\n\n").getBytes());
//        
        for (int i=min; i<=max; i++) {
            String str = "";
            str = "" + i + "\t" + getCount(i)+"\n";
            out.write(str.getBytes());
        }        
    }
}
