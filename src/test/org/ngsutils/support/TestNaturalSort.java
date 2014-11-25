package org.ngsutils.support;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

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

        StringUtils.naturalSort(str);
        
        for (int i=0; i< str.length; i++) {
            assertEquals(correct[i], str[i]);
        }
    }
    
    @Test
    public void testNaturalSortList() {
        String[] str = new String[] { "chr1", "chrY", "chr10", "chr3", "chrX", "chr2", "chr20" };
        String[] correct = new String[] { "chr1", "chr2", "chr3", "chr10", "chr20", "chrX", "chrY" };

        List<String> list = new ArrayList<String>();
        for (String s: str) {
            list.add(s);
        }
        
        List<String> newlist = StringUtils.naturalSort(list);
        for (int i=0; i< str.length; i++) {
            assertEquals(correct[i], newlist.get(i));
        }
    }

}
