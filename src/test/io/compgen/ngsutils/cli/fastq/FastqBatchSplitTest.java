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
        assertTrue(FastqDemux.matches("ABCABC", "ABCABC", 0, false));
        assertFalse(FastqDemux.matches("ABCABD", "ABCABC", 0, false));
        assertTrue(FastqDemux.matches("ABCABD", "ABCABC", 1, false));
        assertTrue(FastqDemux.matches("DBCABD", "ABCABC", 2, false));
        assertFalse(FastqDemux.matches("DDCABD", "ABCABC", 2, false));
    }

    @Test
    public void testGenerateMismatchPatterns() {
        List<String> l1 = FastqDemux.generateMismatchPatterns("ABCABC", 1);
        for (String s: l1) {
            System.out.println(s);
        }
        Set<String> hs1 = new HashSet<String>();
        hs1.addAll(l1);
        assertEquals(l1.size(), hs1.size());
        
        List<String> l2 = FastqDemux.generateMismatchPatterns("ABCABC", 2);
        for (String s: l2) {
            System.out.println(s);
        }
        Set<String> hs2 = new HashSet<String>();
        hs2.addAll(l2);
        assertEquals(l2.size(), hs2.size());
        
    }

    @Test
    public void testMatchesWildcard() {
        assertFalse(FastqDemux.matches("ACGTACGT", "NCGTACGT", 0, false));
        assertTrue(FastqDemux.matches("ACGTACGT", "NCGTACGT", 0, true));
        assertTrue(FastqDemux.matches("ACGTACGT", "NNGTACGT", 0, true));
        assertTrue(FastqDemux.matches("ACGTACGT", "NNNTACGT", 0, true));
        assertTrue(FastqDemux.matches("ACGTACGT", "NNNNACGT", 0, true));
    }

}
