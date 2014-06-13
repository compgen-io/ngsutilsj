package org.ngsutils.support;

import org.apache.commons.math3.distribution.PoissonDistribution;
import static org.apache.commons.math3.util.CombinatoricsUtils.factorialDouble;

public class Statistics {
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
}
