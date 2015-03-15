package org.ngsutils.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.compgen.ngsutils.annotation.BEDAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeAnnotation;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.annotation.BEDAnnotationSource.BEDAnnotation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.support.IterUtils;
import io.compgen.ngsutils.support.StringUtils;

import org.junit.Before;
import org.junit.Test;

public class TestBEDAnnotation {
    BEDAnnotationSource bed;
    
    @Before
    public void setUp() throws Exception {
        bed = new BEDAnnotationSource(getClass().getClassLoader().getResourceAsStream("org/ngsutils/annotation/test.bed"));
    }

    @Test
    public void testHasAnnotation() {
        for (GenomeAnnotation<BEDAnnotation> ann:IterUtils.wrapIterator(bed.iterator())) {
            System.err.println(ann);
            
        }
        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 1000)));
        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 1001)));
        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 3001)));
        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 1999)));
        assertFalse(bed.hasAnnotation(new GenomeSpan("chr1", 2000)));
        assertFalse(bed.hasAnnotation(new GenomeSpan("chr1", 2001)));

        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 900, 1100)));
        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 1900, 2100)));

        assertFalse(bed.hasAnnotation(new GenomeSpan("chr1", 2000, 2100)));

        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 1000, 1001, Strand.PLUS)));
        assertFalse(bed.hasAnnotation(new GenomeSpan("chr1", 1000, 1001, Strand.MINUS)));

        assertTrue(bed.hasAnnotation(new GenomeSpan("chr1", 3000, 3001, Strand.MINUS)));
        assertFalse(bed.hasAnnotation(new GenomeSpan("chr1", 3000, 3001, Strand.PLUS)));

    }

    @Test
    public void testfindAnnotation() {
        assertEquals("foo", StringUtils.join(";", bed.findAnnotation(new GenomeSpan("chr1", 1000))));
        assertEquals("bar", StringUtils.join(";", bed.findAnnotation(new GenomeSpan("chr1", 3000))));
    }
}
