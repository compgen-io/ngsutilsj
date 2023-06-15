package io.compgen.ngsutils.tabix.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;

import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.stats.StatUtils;
import io.compgen.ngsutils.tabix.TabixFile;

public class BedGraphTabAnnotator implements TabAnnotator {
    private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();

    private String name;
    private TabixFile tabix;
    private boolean median = false;
    private boolean max = false;
    private boolean min = false;
    private boolean all = true;
    private boolean mean = false;
    
    public BedGraphTabAnnotator(String name, String fname) throws IOException {
        this.name = name;
        this.tabix = getTabixFile(fname);
    }


    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    
    public void setAll() {
    	this.all = true;

    	this.mean = false;
    	this.median = false;
    	this.min = false;
    	this.max = false;
    }
    
    public void setMean() {
    	this.mean = true;

    	this.all = false;
    	this.median = false;
    	this.min = false;
    	this.max = false;
    }
    public void setMedian() {
    	this.median = true;

    	this.all = false;
    	this.mean = false;
    	this.min = false;
    	this.max = false;
    }    
    public void setMax() {
    	this.max = true;

    	this.all = false;
    	this.mean = false;
    	this.median = false;
    	this.min = false;
    }
    
    public void setMin() {
    	this.min = true;

    	this.all = false;
    	this.mean = false;
    	this.median = false;
    	this.max = false;
    }
    
    @Override
    public String getName() {
    	if (mean) {
    		return name+"_mean";
    	}
    	if (median) {
    		return name+"_median";
    	}
    	if (max) {
    		return name+"_max";
    	}
    	if (min) {
    		return name+"_min";
    	}
    	if (all) {
    		return name+"_all";
    	}
        return name;
    }
    
    @Override
    public String getValue(String ref, int start, int end, String[] qCols) throws IOException {
        List<String> matches = new ArrayList<String>();
        List<Pair<Double, Integer>> vals = new ArrayList<Pair<Double, Integer>>();
        try{
            for (String s: IterUtils.wrap(tabix.query(ref, start, end))) {
                String[] cols = s.split("\t", -1);
                matches.add(cols[3]); // BedGraph has only 4 columns: chrom, start, end, value. We want the value.
                
                int qstart = Integer.parseInt(cols[1]);
                int qend = Integer.parseInt(cols[2]);
                
                // adjust qstart/qend to start/end limits!!!!
                qstart = Math.max(qstart, start);
                qend = Math.min(qend, end);
                
                if (qstart < qend) {
                	// not sure this would ever not be the case,
                	// but because we are adjusting qstart/end above,
                	// let's be explicit
                	
	        		double val = Double.parseDouble(cols[3]);	
	        		vals.add(new Pair<Double, Integer>(val, qend-qstart));
                }
            }
        } catch(DataFormatException e) {
            throw new IOException(e);
        }
        
        if (all) {
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
        } else if ((max || min) && matches.size() > 1) {
        	double[] dvals = new double[vals.size()];
        	
        	for (int i=0; i<vals.size(); i++) {
        		dvals[i] = vals.get(i).one;
        	}
        	if (max) {
        		return ""+StatUtils.max(dvals);
        	} 
        	if (min) {
        		return ""+StatUtils.min(dvals);
        	} 
    	} else if (mean && matches.size() > 0) {
    		return ""+StatUtils.meanSpan(vals);
    	} else if (median && matches.size() > 0) {
    		return ""+StatUtils.medianSpan(vals);
    	}

    	
    	if ((mean || median || max || min) && matches.size() > 1) {

        	// Sort by value
        	vals.sort(new Comparator<Pair<Double, Integer>>() {
				@Override
				public int compare(Pair<Double, Integer> o1, Pair<Double, Integer> o2) {
					return Double.compare(o1.one,  o2.two);
				}});
        	
        	double[] dvals = new double[vals.size()];
        	int[] sizes = new int[vals.size()];
        	
        	for (int i=0; i<vals.size(); i++) {
        		dvals[i] = vals.get(i).one;
        		sizes[i] = vals.get(i).two;
        	}
        	
        }
        
        return StringUtils.join(",", matches);
    }

    public void close() throws IOException {
        tabix.close();
    }
    
}
