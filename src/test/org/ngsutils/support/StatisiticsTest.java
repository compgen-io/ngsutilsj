package org.ngsutils.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.DecimalFormat;

import org.apache.commons.math3.util.CombinatoricsUtils;
import org.junit.Test;

public class StatisiticsTest {

    @Test
    public void testFisher() {
        assertEquals("0.001346", new DecimalFormat("0.000000").format(Statistics.fisherExact(1,9,11,3)));

    }
    @Test
    public void testPpois() {
        assertEquals("0.01831564", new DecimalFormat("0.00000000").format(Statistics.ppois(0,4)));
        assertEquals("0.09157819", new DecimalFormat("0.00000000").format(Statistics.ppois(1,4)));
        assertEquals("0.2381033", new DecimalFormat("0.0000000").format(Statistics.ppois(2,4)));
        assertEquals("0.4334701", new DecimalFormat("0.0000000").format(Statistics.ppois(3,4)));
        assertEquals("0.6288369", new DecimalFormat("0.0000000").format(Statistics.ppois(4,4)));
    }

    @Test
    public void testDpois() {
        assertEquals("0.01831564", new DecimalFormat("0.00000000").format(Statistics.dpois(0,4)));
        assertEquals("0.07326256", new DecimalFormat("0.00000000").format(Statistics.dpois(1,4)));
        assertEquals("0.1465251", new DecimalFormat("0.0000000").format(Statistics.dpois(2,4)));
        assertEquals("0.1953668", new DecimalFormat("0.0000000").format(Statistics.dpois(3,4)));
        assertEquals("0.1953668", new DecimalFormat("0.0000000").format(Statistics.dpois(4,4)));
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

}
