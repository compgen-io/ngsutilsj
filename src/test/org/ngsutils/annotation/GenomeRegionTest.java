package org.ngsutils.annotation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ngsutils.bam.Strand;

public class GenomeRegionTest {

    @Test
    public void testContains() {
        GenomeSpan foo = new GenomeSpan("chr1", 100, 200, Strand.PLUS);
        assertTrue(foo.contains("chr1", 100, 101, Strand.PLUS));
        assertTrue(foo.contains("chr1", 100, 150, Strand.PLUS));
        assertTrue(foo.contains("chr1", 100, 200, Strand.PLUS));
        assertTrue(foo.contains("chr1", 199, 200, Strand.PLUS));

        assertFalse(foo.contains("chr1", 50, 250, Strand.PLUS));
        assertFalse(foo.contains("chr1", 50, 150, Strand.PLUS));
        assertFalse(foo.contains("chr1", 250, 300, Strand.PLUS));
        assertFalse(foo.contains("chr1", 150, 250, Strand.PLUS));
        assertFalse(foo.contains("chr2", 100, 200, Strand.PLUS));
        
        assertFalse(foo.contains("chr1", 100, 200, Strand.MINUS));

        assertTrue(foo.contains("chr1", 100, 200, Strand.NONE));
    }

    @Test
    public void testOverlaps() {
        GenomeSpan foo = new GenomeSpan("chr1", 100, 200, Strand.PLUS);

        assertTrue(foo.overlaps("chr1", 50, 150, Strand.PLUS));
        assertTrue(foo.overlaps("chr1", 150, 175, Strand.PLUS));
        assertTrue(foo.overlaps("chr1", 150, 250, Strand.PLUS));
        assertTrue(foo.overlaps("chr1", 50, 250, Strand.PLUS));

        assertFalse(foo.contains("chr1", 250, 300, Strand.PLUS));

    }

}
