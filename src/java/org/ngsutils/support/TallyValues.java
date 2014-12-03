package org.ngsutils.support;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TallyValues {
    private Map<String, Long> map = new TreeMap<String, Long>();

    public TallyValues() {}
    public void incr(String k) {
        if (!map.containsKey(k)) {
            map.put(k,  1l);
        } else {
            map.put(k, map.get(k)+1);
        }
    }
	
    public long getCount(String k) {
        if (map.containsKey(k)) {
            return map.get(k);
        }
        return 0;
    }
    
    public Set<String> keySet() {
        return map.keySet();
    }
    
    public void write(OutputStream out) throws IOException {
        for (String k: map.keySet()) {
            out.write((k+"\t"+getCount(k)+"\n").getBytes());
        }        
    }
}
