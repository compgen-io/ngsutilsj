package io.compgen.ngsutils.support.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.util.CombinatoricsUtils;
import org.junit.Test;

import io.compgen.common.Pair;

public class StatisticsTest {

    @Test
    public void testFisher() {
        assertEquals("0.001346", new DecimalFormat("0.000000").format(StatUtils.fisherExact(1,9,11,3)));

    }
    @Test
    public void testPpois() {
        assertEquals("0.01831564", new DecimalFormat("0.00000000").format(StatUtils.ppois(0,4)));
        assertEquals("0.09157819", new DecimalFormat("0.00000000").format(StatUtils.ppois(1,4)));
        assertEquals("0.2381033", new DecimalFormat("0.0000000").format(StatUtils.ppois(2,4)));
        assertEquals("0.4334701", new DecimalFormat("0.0000000").format(StatUtils.ppois(3,4)));
        assertEquals("0.6288369", new DecimalFormat("0.0000000").format(StatUtils.ppois(4,4)));
    }

    @Test
    public void testDpois() {
        assertEquals("0.01831564", new DecimalFormat("0.00000000").format(StatUtils.dpois(0,4)));
        assertEquals("0.07326256", new DecimalFormat("0.00000000").format(StatUtils.dpois(1,4)));
        assertEquals("0.1465251", new DecimalFormat("0.0000000").format(StatUtils.dpois(2,4)));
        assertEquals("0.1953668", new DecimalFormat("0.0000000").format(StatUtils.dpois(3,4)));
        assertEquals("0.1953668", new DecimalFormat("0.0000000").format(StatUtils.dpois(4,4)));
    }

    @Test
    public void testFactorial() {
        assertEquals(1, CombinatoricsUtils.factorial(0));
        assertEquals(1, CombinatoricsUtils.factorial(1));
        assertEquals(2, CombinatoricsUtils.factorial(2));
        assertEquals(6, CombinatoricsUtils.factorial(3));
        assertEquals(24, CombinatoricsUtils.factorial(4));
        assertEquals(3_628_800, CombinatoricsUtils.factorial(10));
        assertEquals(2_432_902_008_176_640_000l, CombinatoricsUtils.factorial(20));
        assertTrue(9.332621544395286E157 == CombinatoricsUtils.factorialDouble(100));
    }

    @Test
    public void testCummulativeMin() {
        double[] test = new double[] { 3, 2, 3, 2, 1, 1, 3, 0, 3 };
        double[] correct = new double[] { 3, 2, 2, 2, 1, 1, 1, 0, 0 };
        assertTrue(Arrays.equals(correct, StatUtils.cummulativeMin(test)));
    }
    
    @Test
    public void testCummulativeMax() {
        double[] test = new double[] { 0, 2, 3, 2, 1, 1, 4, 0, 3 };
        double[] correct = new double[] { 0,2, 3, 3, 3, 3, 4, 4, 4 };
        assertTrue(Arrays.equals(correct, StatUtils.cummulativeMax(test)));
    }
    
    @Test
    public void testArrayMax() {
        double[] test = new double[] { 0, 2, 3, 2, 1, 1, 4, 0, 3 };
        double[] correct = new double[] { 3, 3, 3, 3, 3, 3, 4, 3, 3 };
        assertTrue(Arrays.equals(correct, StatUtils.arrayMax(3, test)));
    }
    
    @Test
    public void testArrayMin() {
        double[] test = new double[] { 0, 2, 3, 2, 1, 1, 4, 0, 3 };
        double[] correct = new double[] { 0, 2, 3, 2, 1, 1, 3, 0, 3 };
        assertTrue(Arrays.equals(correct, StatUtils.arrayMin(3, test)));
    }
    
    @Test
    public void testCummulativeSum() {
        double[] test = new double[] { 0, 2, 3, 2, 1, 1, 4, 0, 3 };
        double[] correct = new double[] { 0, 2, 5, 7, 8, 9, 13, 13, 16 };
        assertTrue(Arrays.equals(correct, StatUtils.cummulativeSum(test)));
    }
    
    @Test
    public void testSortRank() {
        double[] test = new double[] { 1, 3, 2, 4, 3 };
        int[] correct = new int[] { 1, 3, 2, 5, 4 };  // note: ties are handled as min(rank) for each member, so there is no "4"
        int[] ranks = StatUtils.sortOrder(test);
        assertTrue(Arrays.equals(correct, ranks));

        double[] pvalues = new double[] { 0.0, 0.01, 0.029, 0.03, 0.031, 0.05, 0.069, 0.07, 0.071, 0.09, 0.1 };   
        assertTrue(Arrays.equals(new int[] { 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1}, StatUtils.sortOrder(pvalues, true)));

        double[] pvalues1 = new double[] { 0.0, 0.01, 0.05, 0.029, 0.03, 0.031, 0.09, 0.069, 0.07, 0.071, 0.1 };
        assertTrue(Arrays.equals(new int[] { 11, 7, 10, 9, 8, 3, 6, 5, 4, 2, 1}, StatUtils.sortOrder(pvalues1, true)));
    }
    

    @Test
    public void testBH() {
        double[] pvalues = new double[] { 0.0, 0.01, 0.029, 0.03, 0.031, 0.05, 0.069, 0.07, 0.071, 0.09, 0.1 };
        double[] corrected = new double[] { 0.00000000, 0.05500000, 0.06820000, 0.06820000, 0.06820000, 0.08677778, 0.08677778, 0.08677778, 0.08677778, 0.09900000, 0.10000000 }; // from R p.adjust('BH')
        double[] test = StatUtils.benjaminiHochberg(pvalues);

        assertTrue(StatUtils.deltaEquals(corrected, test, 0.0001));


        // slightly re-ordered version... Should end up with the same values!
        double[] pvalues1 = new double[] { 0.0, 0.01, 0.05, 0.029, 0.03, 0.031, 0.09, 0.069, 0.07, 0.071, 0.1 };
        double[] corrected1 = new double[] { 0.00000000, 0.05500000, 0.08677778, 0.06820000, 0.06820000, 0.06820000, 0.09900000, 0.08677778, 0.08677778, 0.08677778, 0.10000000 }; // from R p.adjust('BH')
        double[] test1 = StatUtils.benjaminiHochberg(pvalues1);

        assertTrue(StatUtils.deltaEquals(corrected1, test1, 0.0001));
    }
    
    @Test
    public void testPropTestPval() {
        assertEquals("0.3072681", new DecimalFormat("0.0000000").format(StatUtils.calcProportionalPvalue(5, 11, 16, 15)));
        assertEquals("0.8706", new DecimalFormat("0.0000").format(StatUtils.calcProportionalPvalue(5, 10, 20, 30)));
    }
    
    @Test
    public void testMeanMedianSpan() {
    	List<Pair<Double, Integer>> l1 = new ArrayList<Pair<Double, Integer>>();
    	l1.add(new Pair<Double, Integer>(2.0,2));
    	l1.add(new Pair<Double, Integer>(1.0,3));
    	l1.add(new Pair<Double, Integer>(3.0,5));

    	List<Pair<Double, Integer>> l2 = new ArrayList<Pair<Double, Integer>>();
    	l2.add(new Pair<Double, Integer>(2.0,2));
    	l2.add(new Pair<Double, Integer>(1.0,3));
    	l2.add(new Pair<Double, Integer>(3.0,4));

    	int[] size = new int[] {2,3,5};
    	double[] dvals = new double[] {2.0, 1.0, 3.0};
    
    	
//    	System.out.println(StringUtils.join(",", l1));
//    	System.out.println("Mean: " + StatUtils.meanSpan(l1));
//    	System.out.println("Median: " + StatUtils.medianSpan(l1));

        assertEquals(2.2, StatUtils.meanSpan(l1), 0.000001);
        assertEquals(2.5, StatUtils.medianSpan(l1), 0.000001);

//    	System.out.println("======");
//    	
//    	System.out.println(StringUtils.join(",", l2));
//    	System.out.println("Mean: " + StatUtils.meanSpan(l2));
//    	System.out.println("Median: " + StatUtils.medianSpan(l2));
//
        assertEquals(19.0/9, StatUtils.meanSpan(l2), 0.000001);
        assertEquals(2.0, StatUtils.medianSpan(l2), 0.000001);

//        System.out.println("======");
//
//    	System.out.println("size: " + StringUtils.join(",", size));
//    	System.out.println("dval: " + StringUtils.join(",", dvals));
//    	System.out.println("Mean: " + StatUtils.meanSpan(dvals, size));
//    	System.out.println("Median: " + StatUtils.medianSpan(dvals, size));

        assertEquals(2.2, StatUtils.meanSpan(dvals, size), 0.000001);
        assertEquals(2.5, StatUtils.medianSpan(dvals, size), 0.000001);

    }
    
//    @Test
//    public void testVarCall() {
//    	for (int i=1; i<=30; i++) {
//    		innerVarCall(i, 30, 0.02, 0.05);
//    	}
//
////    	for (int i=1; i<=100; i++) {
////    		innerVarCall(i, 100, 0.02, 0.05);
////    	}
//}
//    
//    public void innerVarCall(int alleleCount, int totalCount, double errorRate, double minAF) {
//    	System.out.println("Alleles     : " + alleleCount +" / " + totalCount);
//    	System.out.println("Homozygous  : "+StatUtils.calcPvalueHomozygous(alleleCount, totalCount, errorRate));
//    	System.out.println("Heterozygous: "+StatUtils.calcPvalueHeterozygous(alleleCount, totalCount, errorRate));
//    	System.out.println("MinAF       : "+StatUtils.calcPvalueIsError(alleleCount, totalCount, errorRate));
//    }
}
