package io.compgen.ngsutils.cli.fastq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FastqBatchSplitTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testMatches() {
        assertTrue(FastqBatchSplit.matches("ABCABC", "ABCABC", 0));
        assertFalse(FastqBatchSplit.matches("ABCABD", "ABCABC", 0));
        assertTrue(FastqBatchSplit.matches("ABCABD", "ABCABC", 1));
        assertTrue(FastqBatchSplit.matches("DBCABD", "ABCABC", 2));
        assertFalse(FastqBatchSplit.matches("DDCABD", "ABCABC", 2));
    }

    @Test
    public void testGenerateMismatchPatterns() {
        List<String> l1 = FastqBatchSplit.generateMismatchPatterns("ABCABC", 1);
        for (String s: l1) {
            System.out.println(s);
        }
        Set<String> hs1 = new HashSet<String>();
        hs1.addAll(l1);
        assertEquals(l1.size(), hs1.size());
        
        List<String> l2 = FastqBatchSplit.generateMismatchPatterns("ABCABC", 2);
        for (String s: l2) {
            System.out.println(s);
        }
        Set<String> hs2 = new HashSet<String>();
        hs2.addAll(l2);
        assertEquals(l2.size(), hs2.size());
        
    }

}
