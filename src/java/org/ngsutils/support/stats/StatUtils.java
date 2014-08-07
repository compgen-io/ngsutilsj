package org.ngsutils.support.stats;

import static org.apache.commons.math3.util.CombinatoricsUtils.factorialDouble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.ngsutils.support.Pair;

public class StatUtils {
    /**
     * Based on a 2x2 contingency table:
     * 
     *              Group 1     Group 2
     *             ----------|----------
     * Condition 1     A     |    B
     *             ----------|----------
     * Condition 2     C     |    D
     *             ----------|----------
     *  
     * @param A
     * @param B
     * @param C
     * @param D
     * @return Fisher's exact p-value for the given table (does not take in to account more or less extreme tables)
     */
    public static double fisherExact(int A, int B, int C, int D) {
        double AB = factorialDouble(A+B);
        double CD = factorialDouble(C+D);
        double AC = factorialDouble(A+C);
        double BD = factorialDouble(B+D);
        
        double Afact = factorialDouble(A);
        double Bfact = factorialDouble(B);
        double Cfact = factorialDouble(C);
        double Dfact = factorialDouble(D);
        double Nfact = factorialDouble(A+B+C+D);
        
        return (AB * CD * AC * BD) / (Afact * Bfact * Cfact * Dfact * Nfact);
    }
    
    public static double poissonMean(int lambda, int n, float mu) {
        return ppois(n, (int) Math.floor(lambda*mu));
    }

    public static double dpois(int x, int lambda) {
        return new PoissonDistribution(lambda).probability(x);
        //return (Math.pow(lambda, x) / CombinatoricsUtils.factorialDouble(x)) * Math.exp(-1*lambda); 
    }

    public static double ppois(int x, int lambda) {
        return new PoissonDistribution(lambda).cumulativeProbability(x);
//        double acc = 0.0;
//        for (int i=0; i<=x; i++) {
//            acc += (Math.pow(lambda, i) / CombinatoricsUtils.factorialDouble(i)); 
//        }
//        return acc * Math.exp(-lambda);
    }

    /* Use CombinatoricsUtils.factorial instead
    private static Map<Integer, Long> factorialMemoize = new HashMap<Integer, Long> ();
    public static long factorial(int n) {
        if (n == 0) {
            return 1;
        }
        if (factorialMemoize.containsKey(n)) {
            return factorialMemoize.get(n);
        }
        long acc = 1;
        for (int i=1; i<=n; i++) {
            acc *= i;
        }
        factorialMemoize.put(n, acc);
        return acc;
    }
    */
    
    public static double[] bonferroni(double[] pvalues) {
        double[] out = new double[pvalues.length];
        for (int i=0; i<pvalues.length; i++) {
            out[i] = pvalues[i] * pvalues.length;
        }
        return arrayMin(1, out);
    }

    /**
    * Correct p-values for multiple testing using Benjamini-Hochberg
    * 
    * Benjamini, Yoav; Hochberg, Yosef (1995). "Controlling the false discovery rate: a practical and powerful approach to multiple testing". Journal of the Royal Statistical Society, Series B 57 (1): 289–300. MR 1325392
    * 
    * See also: http://stackoverflow.com/questions/7450957/how-to-implement-rs-p-adjust-in-python
    *
    * R-code:
    * BH = {
    *      i <- lp:1L
    *      o <- order(p, decreasing = TRUE)
    *      ro <- order(o)
    *      pmin(1, cummin( n / i * p[o] ))[ro]
    *      },
    * 
    */

    public static double[] benjaminiHochberg(double[] pvalues) {
        int n = pvalues.length;

        int[] order = sortOrder(pvalues, true);
        int[] ro = sortOrder(order);
        
        double[] tmp = new double[n];
        for (int i=0; i<n; i++) {
            int rank = n - i;
            tmp[i] = (double) n / rank * pvalues[order[i]-1];
        }

        tmp = arrayMin(1, cummulativeMin(tmp));

        double[] out = new double[n];
        for (int i=0; i<n; i++) {
            out[i] = tmp[ro[i]-1];
        }

        return out;
        
    }

    public static int[] sortOrder(double[] values) {
        return sortOrder(values, false);
    }
    /**
     * Returns the indexes for a correct sorted order (Ascending or descending)
     * 
     * Note: this operates like the R method "order"
     * @param values
     * @param decreasing
     * @return
     */
    public static int[] sortOrder(double[] values, boolean decreasing) {
        List<Pair<Double,Integer>> vals = new ArrayList<Pair<Double, Integer>>();
        for (int i=0; i< values.length; i++) {
            vals.add(new Pair<Double, Integer>(values[i], i));
        }

        if (decreasing) {
            Collections.sort(vals, Collections.reverseOrder());
        } else {
            Collections.sort(vals);
        }
        
        int[] ranks = new int[values.length];
        for (int i=0; i<values.length; i++) {
            ranks[i] = vals.get(i).two+1;
        }        
        return ranks;
    }
    
    public static int[] sortOrder(int[] values) {
        return sortOrder(values, false);
    }
    public static int[] sortOrder(int[] values, boolean decreasing) {
        List<Pair<Integer,Integer>> vals = new ArrayList<Pair<Integer, Integer>>();
        for (int i=0; i< values.length; i++) {
            vals.add(new Pair<Integer, Integer>(values[i], i));
        }

        if (decreasing) {
            Collections.sort(vals, Collections.reverseOrder());
        } else {
            Collections.sort(vals);
        }
        
        int[] ranks = new int[values.length];
        for (int i=0; i<values.length; i++) {
            ranks[i] = vals.get(i).two+1;
        }
        return ranks;
    }
    
    
    /**
     * returns the cummulative-minima for an array
     * @param pvalues
     * @return
     */
    public static double[] cummulativeMin(double[] vals) {
        double[] out = new double[vals.length];
        double min = Double.MAX_VALUE;
        for (int i=0; i< vals.length; i++) {
            if (i ==0 || vals[i] < min) {
                min = vals[i];
            }
            out[i] = min;
        }

        return out;
    }
    
    /**
     * returns the cummulative-maxima for an array
     * @param pvalues
     * @return
     */
    public static double[] cummulativeMax(double[] vals) {
        double[] out = new double[vals.length];
        double max = Double.MIN_VALUE;
        for (int i=0; i< vals.length; i++) {
            if (i ==0 || vals[i] > max) {
                max = vals[i];
            }
            out[i] = max;
        }

        return out;
    }
    
    /**
     * returns the cummulative-sum for an array
     * @param pvalues
     * @return
     */
    public static double[] cummulativeSum(double[] vals) {
        double[] out = new double[vals.length];
        double acc = 0;
        for (int i=0; i< vals.length; i++) {
            acc += vals[i];
            out[i] = acc;
        }

        return out;
    }

    /**
     * Applies a min value to all elements in an array 
     * @param values
     * @return
     */
    public static double[] arrayMin(double minVal, double[] vals) {
        double[] out = new double[vals.length];
        for (int i=0; i< vals.length; i++) {
            out[i] = Math.min(minVal, vals[i]);
        }

        return out;
    }

    /**
     * Applies a max value to all elements in an array 
     * @param values
     * @return
     */
    public static double[] arrayMax(double maxVal, double[] vals) {
        double[] out = new double[vals.length];
        for (int i=0; i< vals.length; i++) {
            out[i] = Math.max(maxVal, vals[i]);
        }

        return out;
    }
 
    public static boolean deltaEquals(double[] one, double[] two, double delta) {
        if (one.length != two.length) {
            return false;
        }
        
        for (int i=0; i<one.length; i++) {
            if (Math.abs(one[i]-two[i]) > delta) {
                return false;
            }
        }
        return true;
    }
    
}