package org.ngsutils.support.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * You add permuted scores to this class, then you can query the distribution to find a 
 * p-value for a test-score.
 *  
 * Assumes a two-tailed p-value calculation
 * 
 * @author mbreese
 *
 */
public class PermutedNullDistribution {
    private boolean set = false;
    private List<Double> scores = new ArrayList<Double>();
    private double[] scoreAr = null;
    private int pseudocount = 1;
    private int tails = 2;
    private String name = "Permuted Null Distribution";
    
    public String toString() {
        return name + " ("+ ((scores!=null) ? scores.size(): scoreAr.length)+")";
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setPseudocount(int val) {
        this.pseudocount = val;
    }
    
    public void add(double score) {
        if (!set) {
            if (tails == 2) {
                scores.add(Math.abs(score));
            } else {
                scores.add(score);
            }
        } else {
            throw new RuntimeException("Cannot add new scores - pool is fixed!");
        }
    }
    
    public void set() {
        this.set = true;
        scoreAr = new double[scores.size()];

        Collections.sort(scores, Collections.reverseOrder());
        for (int i=0; i<scores.size(); i++) {
            scoreAr[i] = scores.get(i);
        }
        scores = null;
    }
    
    public double pvalue(double test) {
        if (!set) {
            set();
        }
        int i;
        test = Math.abs(test);
        for (i=0; i<scoreAr.length && scoreAr[i]>= test; i++){}
        return Math.min(1, ((double) pseudocount + i) / scoreAr.length);
    }
    
}
