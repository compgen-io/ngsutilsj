package io.compgen.ngsutils.support;

import static org.junit.Assert.assertEquals;
import io.compgen.support.StringUtils;

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
        String[] str = "chr1 chr2 chr3 chr4 chr5 chr6 chr7 chr8 chr9 chr12 chr16 chr17 chr20 chr10 chr21 chr11 chr13 chr14 chr15 chr18 chr19 chr22 chrM chrX chrY".split(" ");
        String[] correct = new String[] { "chr1", "chr2", "chr3", "chr4", "chr5", "chr6", 
                                          "chr7", "chr8", "chr9", "chr10", "chr11", "chr12",
                                          "chr13", "chr14", "chr15", "chr16", "chr17", "chr18",
                                          "chr19", "chr20", "chr21", "chr22", "chrM", "chrX", "chrY" };

        System.out.println("Unsorted: " + StringUtils.join(", ",str));
        StringUtils.naturalSort(str);
        System.out.println("Sorted  : " + StringUtils.join(", ",str));
        
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
