package org.ngsutils.support;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestNaturalSort {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testNaturalSort() {
        String[] str = new String[] { "chr1", "chrY", "chr10", "chr3", "chrX", "chr2", "chr20" };
        String[] correct = new String[] { "chr1", "chr2", "chr3", "chr10", "chr20", "chrX", "chrY" };

        String[] retval = NaturalSort.naturalSort(str);

//        System.err.println("Got: "+StringUtils.join(", ", retval));
        
        for (int i=0; i< retval.length; i++) {
            assertEquals(correct[i], retval[i]);
        }
    }

}
