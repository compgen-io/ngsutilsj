package io.compgen.ngsutils.support.stats;

import static org.apache.commons.math3.util.CombinatoricsUtils.factorialDouble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.PascalDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;

import io.compgen.common.ComparablePair;
import io.compgen.common.Pair;

public class StatUtils {
    
    public static final double log2Factor = Math.log(2);

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
    
	public static double phred(double val) {
		if (val <= 0.0) {
			return 255.0;
		} else if (val >= 1.0) {
			return 0.0;
		} else {
			return -10 * Math.log10(val);
		}
	}

    
    public static double poissonMean(double lambda, int n, float mu) {
        return ppois(n, (int) Math.floor(lambda*mu));
    }

    public static double dpois(int x, double lambda) {
        return new PoissonDistribution(lambda).probability(x);
        //return (Math.pow(lambda, x) / CombinatoricsUtils.factorialDouble(x)) * Math.exp(-1*lambda); 
    }

    public static double ppois(int x, double lambda) {
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
        List<ComparablePair<Double,Integer>> vals = new ArrayList<ComparablePair<Double, Integer>>();
        for (int i=0; i< values.length; i++) {
            vals.add(new ComparablePair<Double, Integer>(values[i], i));
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
        List<ComparablePair<Integer,Integer>> vals = new ArrayList<ComparablePair<Integer, Integer>>();
        for (int i=0; i< values.length; i++) {
            vals.add(new ComparablePair<Integer, Integer>(values[i], i));
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
 
    /**
     * Are the values in the double[] arrays equal (within a given delta) 
     * @param one
     * @param two
     * @param delta
     * @return
     */
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
    
    public static double median(int[] vals) {
        int[] copy = Arrays.copyOf(vals, vals.length);
        Arrays.sort(copy);
        
        if (copy.length % 2 == 0) {
            int acc = copy[copy.length / 2];
            acc += copy[(copy.length / 2)-1];
            return acc / 2.0;
        } else {
            return copy[copy.length / 2];
        }
    }

    public static double median(double[] vals) {
        double[] copy = Arrays.copyOf(vals, vals.length);
        Arrays.sort(copy);
        
        if (copy.length % 2 == 0) {
            double acc = copy[copy.length / 2];
            acc += copy[(copy.length / 2)-1];
            return acc / 2.0;
        } else {
            return copy[copy.length / 2];
        }

    }

    public static double log2(double val) {
        return Math.log(val) / log2Factor;
    }
    
    public static double[] log2(double[] vals) {
        double[] out = new double[vals.length];
        for (int i=0; i<vals.length; i++) {
            out[i] = log2(vals[i]);
        }
        return out;
    }

    
    public static double log2(int val) {
        return Math.log(val) / log2Factor;
    }
    
    public static double[] log2(int[] vals) {
        double[] out = new double[vals.length];
        for (int i=0; i<vals.length; i++) {
            out[i] = log2(vals[i]);
        }
        return out;
    }


    public static double medianInteger(List<Integer> vals) {
        List<Integer> copy = new ArrayList<Integer>(vals);
        Collections.sort(copy);
        
        if (copy.size() % 2 == 0) {
            int acc = copy.get(copy.size() / 2);
            acc +=  copy.get((copy.size() / 2) - 1);
            return acc / 2.0;
        } else {
            return copy.get(copy.size() / 2);
        }
    }

    public static double medianDouble(List<Double> vals) {
        List<Double> copy = new ArrayList<Double>(vals);
        Collections.sort(copy);
        
        if (copy.size() % 2 == 0) {
            Double acc = copy.get(copy.size() / 2);
            acc +=  copy.get((copy.size() / 2) - 1);
            return acc / 2.0;
        } else {
            return copy.get(copy.size() / 2);
        }
    }

    
    public static long sum(int[] vals) {
        long acc = 0;
        for (int i=0; i< vals.length; i++) {
            acc += vals[i];
        }
        return acc;
    }

    public static class MeanStdDev {
        public final double mean;
        public final double stddev;
        
        private MeanStdDev(double mean, double stddev) {
            this.mean = mean;
            this.stddev = stddev;
        }
    }

    public static MeanStdDev calcMeanStdDev(double[] vals) {
        double acc = 0.0;
        int count = 0;
        for (int i=0; i< vals.length; i++) {
            acc += vals[i];
            count += 1;
        }
        
        double mean = acc / count;
        
        acc = 0.0;
        for (int i=0; i< vals.length; i++) {
            acc += Math.pow(vals[i]-mean, 2);
        }

        return new MeanStdDev(mean, Math.sqrt(acc * 1/(count - 1)));
    }

    public static MeanStdDev calcMeanStdDev(int[] vals) {
        long acc = 0;
        int count = 0;
        for (int i=0; i< vals.length; i++) {
            acc += vals[i];
            count += 1;
        }
        
        double mean = ((double)acc) / count;
        
        acc = 0;
        for (int i=0; i< vals.length; i++) {
            acc += Math.pow(vals[i]-mean, 2);
        }

        return new MeanStdDev(mean, Math.sqrt(acc * 1/(count - 1)));
    }

    


    // Chi Squared dist w/ 1 d.f. (for pvalue)
    private static ChiSquaredDistribution csd = null;
    
    /**
     * https://en.wikipedia.org/wiki/Yates%27s_correction_for_continuity
     * @param vcfRef
     * @param vcfAlt
     * @param ref
     * @param alt
     * @return
     */
    public static double calcProportionalPvalue(int a, int b, int c, int d) {
        if (csd == null) {
            csd = new ChiSquaredDistribution(1);
        }
        
        double Na = a + b;
        double Nb = c + d;
        double Ns = a + c;
        double Nf = b + d;
        
        double N = a + b + c + d;
        
        double num = N * Math.pow(Math.max(0, Math.abs(a*d - b*c) - N/2),2);
        double dem = Ns * Nf * Na * Nb;

//        System.err.println("N="+N+", num="+num+", dem="+dem);
        
        double chi = num / dem;
        double pvalue=1-csd.cumulativeProbability(chi);
        
//        System.err.println("a="+a+", b="+b+", c="+c+", d="+d+", X="+chi+", pval="+ pvalue);
        
        return pvalue;
    }
    
    
    /**
     * Give a list of Double/Integer pairs, treat the double as a value and the integer as the
     * number of times the double is present in a list.
     * 
     * Example:
     *     The mean of: [(3,2), (1,2), (4,1)] should be ((3 * 2) + (1*2) + (4*1)) / 5
     * 
     * @param vals
     * @return
     */
    public static double meanSpan(List<Pair<Double, Integer>> vals) {
    	double acc = 0.0;
    	int count = 0;
    	
    	for (Pair<Double, Integer> val: vals) {
    		acc += (val.one * val.two);
    		count += val.two;
    	}
    	return acc / count;
    }

    public static double meanSpan(double[] dvals, int[] sizes) {
    	if (dvals.length != sizes.length) {
    		throw new RuntimeException("Mismatched vals/sizes!");
    	}

    	double acc = 0.0;
    	int count = 0;
    	
    	for (int i=0; i< dvals.length; i++) {
    		acc += (dvals[i] * sizes[i]);
    		count += sizes[i];
    	}
    	return acc / count;
    }

    /**
     * Return the median value for a value/size pair
     * 
     * Note: we call this from the double[], int[] version as well so we can do a paired sort on the
     * tuple.
     * 
     * @param vals
     * @return
     */
    public static double medianSpan(List<Pair<Double, Integer>> spanVals) {
    	if (spanVals == null || spanVals.size() == 0) {
    		return Double.NaN;
    	}

    	// Make a copy of the list... we need to sort it and don't want to alter the incoming list.
    	List<Pair<Double, Integer>> vals = new ArrayList<Pair<Double, Integer>>(spanVals.size());
    	for (Pair<Double, Integer> tup : spanVals) {
    		vals.add(new Pair<Double, Integer>(tup.one, tup.two));
    	}
    	return medianSpanInplace(vals);
    }
    

    public static double medianSpan(double[] dvals, int[] sizes) {
    	if (dvals.length != sizes.length) {
    		throw new RuntimeException("Mismatched vals/sizes!");
    	}
    	
    	List<Pair<Double, Integer>> vals = new ArrayList<Pair<Double, Integer>>(dvals.length);
    	for (int i=0; i<dvals.length; i++) {
    		vals.add(new Pair<Double, Integer>(dvals[i], sizes[i]));
    	}
    	
    	return medianSpanInplace(vals);

    }

    /**
     * This method will calculate the median using the argument list in-place (so use it after
     * creating a copy in a public method)
     * 
     * @param vals
     * @return
     */
    private static double medianSpanInplace(List<Pair<Double, Integer>> vals) {

    	vals.sort(new Comparator<Pair<Double, Integer>>() {
			@Override
			public int compare(Pair<Double, Integer> o1, Pair<Double, Integer> o2) {
				return Double.compare(o1.one,  o2.one);
			}});
    	
    	int total = 0;
    	for (Pair<Double, Integer> tup : vals) {
    		total += tup.two;
    	}
    	
    	if (total % 2 == 0) {
        	int mid1 = total / 2;
        	int mid2 = (total / 2) + 1;
        	
    		int cur = 0;
    		double val1 = Double.NaN;
    		boolean found = false;
    		
        	for (Pair<Double, Integer> tup : vals) {        		
        		cur += tup.two;
        		if (!found && cur >= mid1) {
        			found = true;
        			val1 = tup.one;
        		}
        		if (cur >= mid2) {
        			return (val1 + tup.one) / 2;
        		}
        	}
    	} else {
        	int mid = (total / 2) + 1;        	
    		int cur = 0;
    		
        	for (Pair<Double, Integer> tup : vals) {        		
        		cur += tup.two;
        		if (cur >= mid) {
        			return tup.one;
        		}
        	}
    	}
    	return Double.NaN;    	
    }
    
	public static double max(double[] dvals) {
		double thres = dvals[0];
		for (int i=1; i<dvals.length; i++) {
			if (dvals[i] > thres) {
				thres = dvals[i];
			}
		}
		return thres;
	}

	public static double min(double[] dvals) {
		double thres = dvals[0];
		for (int i=1; i<dvals.length; i++) {
			if (dvals[i] < thres) {
				thres = dvals[i];
			}
		}
		return thres;
	}
    
    
//    public static class Trendline {
//        public final double slope;
//        public final double intercept;
//        public final double mse;
//        public final double rsquare;
//        
//        public Trendline(double slope, double intercept, double mse, double rsquare) {
//            this.slope = slope;
//            this.intercept = intercept;
//            this.mse = mse;
//            this.rsquare = rsquare;
//        }
//    }
//    
//    public static Trendline linearRegression(double[] x, double[] y) {
//        SimpleRegression reg = new SimpleRegression();
//        for (int i=0; i<x.length; i++) {
//            reg.addData(x[i], y[i]);
//        }
//        
//        return new Trendline(reg.getSlope(), reg.getIntercept(), reg.getMeanSquareError(), reg.getRSquare());
//    }
//    

	public static double pnbinom(int x, int r, double p) {
		return new PascalDistribution(r, p).cumulativeProbability(x);
	}
	
	public static double dnbinom(int x, int r, double p) {
		return new PascalDistribution(r, p).probability(x);
	}
	


    /** 
     * For a homozygous variant, we will calculate the likelihood that the *minor*
     * allele is all error.
     * 
     * NegBinom: number of failures before a number of successes, given a known success rate.
     * 
     * So, what we will really calculate is:
     * 
     * failures = total - allelecount
     * number of successes = allelecount
     * success_rate = 1-(error_rate)
     * error_rate = # errors / (total) 
     * 
     * The final result is 1- this probability.
     * 
     * @param alleleCount
     * @param totalCount
	 * @param errorRate
     * @return
     */
	public static double calcPvalueHomozygous(int alleleCount, int totalCount, double errorRate) {
		double errorCount = ((Double)Math.ceil(totalCount * errorRate)).intValue();
		if (errorCount <= 0.0) {
			errorCount = 1.0;		
		}

		double successProb = 1 - (errorCount / totalCount); 
		int failures = totalCount - alleleCount;
		
		return dnbinom(failures, totalCount, successProb);
	}

	/**
	 * For heterozygous variants, we will calculate the likelihood that the alleleCount
	 * is a heterozygous call (AF=0.5).
	 * 
     * 
	 * @param alleleCount
	 * @param totalCount
	 * @return
	 */
	public static double calcPvalueHeterozygous(int alleleCount, int totalCount) { //, double errorRate) {
		return StatUtils.dnbinom(alleleCount, totalCount / 2, 0.5);
	}

	/** 
	 * For background, we will calculate the likelihood that the call is
	 * at the error rate
	 * 
	 * @param alleleCount
	 * @param totalCount
	 * @param errorRate
	 * @return
	 */
	public static double calcPvalueBackground(int alleleCount, int totalCount, double errorRate) {
		double errorCount = ((Double)Math.ceil(totalCount * errorRate)).intValue();
		if (errorCount <= 0.0) {
			errorCount = 1.0;
		}
		
		double successProb = 1 - (errorCount / totalCount); 
		if (successProb == 0.0) {
			// errorCount == totalCount, which means totalCount = 1
			return 1.0; 
		}
 
		double p = StatUtils.dnbinom(alleleCount, totalCount, successProb);
		return p;

	}

	/** 
	 * For present calls, we will calculate the likelihood that the call is
	 * at the allele frequency.
	 * 
	 * Failures = the other calls (total - alleleCount)
	 * 
	 * @param alleleCount
	 * @param totalCount
	 * @param errorRate
	 * @return
	 */
	public static double calcPvaluePresent(int alleleCount, int totalCount) {
		double successProb = (double) alleleCount / totalCount;
		int failures = totalCount - alleleCount;

		double p = StatUtils.dnbinom(failures, alleleCount, successProb);
		return p;

	}

}
