package org.ngsutils.bam;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ngsutils.junction.JunctionDiff;

public class JunctionDiffTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCalcPermutations() {
        assertEquals(34, JunctionDiff.permuteGroups(new Integer[]{1,1,1,2,2,2,2}).size());
    }
}
