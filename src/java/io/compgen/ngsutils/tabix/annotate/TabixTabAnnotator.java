package io.compgen.ngsutils.tabix.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixFile;

public class TabixTabAnnotator implements TabAnnotator {
    private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();

    private String name;
    private TabixFile tabix;
    private int col;
    private boolean collapse;
    private boolean first;
    private boolean mean;
    private boolean median;
    private boolean count;
    
    public TabixTabAnnotator(String name, String fname, int col) throws IOException {
        this.name = name;
        this.tabix = getTabixFile(fname);
        this.col = col;
    }

    public TabixTabAnnotator(String name, String fname, String colName) throws IOException {
        this.name = name;
        this.tabix = getTabixFile(fname);
        
        int col = this.tabix.findColumnByName(colName);
        if (col == -1) {
        	throw new IOException("Unknown column name: "+ colName);
        }
        
        this.col = col;
    }

    public TabixTabAnnotator(String name, String fname) throws IOException {
        this(name, fname, -1);
    }

    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    
    public void setShowAll() {
    	this.collapse = false;
    	this.first = false;
    	this.mean = false;
    	this.median = false;
    	this.count = false;
    }
    
    public void setCollapse() {
    	this.collapse = true;

    	this.first = false;
    	this.mean = false;
    	this.median = false;
    	this.count = false;
    }
    public void setFirst() {
    	this.first = true;

    	this.collapse = false;
    	this.mean = false;
    	this.median = false;
    	this.count = false;
    }
    public void setMean() {
    	this.mean = true;

    	this.first = false;
    	this.collapse = false;
    	this.median = false;
    	this.count = false;
    }
    public void setMedian() {
    	this.median = true;

    	this.first = false;
    	this.collapse = false;
    	this.mean = false;
    	this.count = false;
    }
    public void setCount() {
    	this.count = true;

    	this.first = false;
    	this.collapse = false;
    	this.mean = false;
    	this.median = false;
    }
    
    @Override
    public String getName() {
    	if (mean) {
    		return name+"_mean";
    	}
    	if (median) {
    		return name+"_median";
    	}
    	if (count) {
    		return name+"_count";
    	}
        return name;
    }
    
    @Override
    public String getValue(String ref, int start, int end, String[] qCols) throws IOException {
        List<String> matches = new ArrayList<String>();
        try{
            for (String s: IterUtils.wrap(tabix.query(ref, start, end))) {
                String[] cols = s.split("\t", -1);
                if (col > -1 && col < cols.length) {
                    matches.add(cols[col]);
                } else {
                    matches.add(name);
                }
            }
        } catch(DataFormatException e) {
            throw new IOException(e);
        }
        
        if (col == -1) {
            if (matches.size()>0) {
                return name;
            }
        }

        if (count) {
            return "" + matches.size();
        }
        if (first && matches.size() > 1) {
            return matches.get(0);
        }
        if (collapse) {
    		// convoluted to maintain the same order...
    		Set<String> uniq = new HashSet<String>();
    		List<String> ret = new ArrayList<String>();
    		for (String v: matches) {
    			if (v != null && !v.equals("")) {
    				if (!uniq.contains(v) ) {
    					uniq.add(v);
    					ret.add(v);
    				}
    			}
    		}
    		
        	
            return StringUtils.join(",", ret);
        }
        if ((mean || median) && matches.size() > 1) {
        	double[] vals = new double[matches.size()];
        	for (int i=0; i<vals.length; i++) {
        		vals[i] = Double.parseDouble(matches.get(i));
        	}
        	
        	if (mean) {
        		return ""+StatUtils.mean(vals);
        	}
        	if (median) {
        		Median med = new Median();
        		return "" + med.evaluate(vals);
        	}
        }
        
        
        return StringUtils.join(",", matches);
    }

    public void close() throws IOException {
        tabix.close();
    }
    
}
