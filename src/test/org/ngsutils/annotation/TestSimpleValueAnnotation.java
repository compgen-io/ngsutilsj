package org.ngsutils.annotation;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ngsutils.annotation.SimpleValueAnnotator.SimpleValueAnnotation;
import org.ngsutils.support.StringUtils;

public class TestSimpleValueAnnotation {
    SimpleValueAnnotator sva;
    
    @Before
    public void setUp() throws Exception {
        sva = new SimpleValueAnnotator();
        sva.addAnnotation("chr2", 100, 200, "X");
        sva.addAnnotation("chr1", 100, 200, "A");
        sva.addAnnotation("chr1", 300, 400, "B");
        sva.addAnnotation("chr1", 500, 600, "C");
        sva.addAnnotation("chr1", 700, 800, "D");
        sva.addAnnotation("chr1", 900, 1000, "E");
        sva.addAnnotation("chr1", 350, 650, "F");
        sva.addAnnotation("chr1", 450, 475, "G");
        sva.addAnnotation("chr1", 460, 525, "H");
//
//        for(GenomeCoordinates coord: sva.allCoordinates()) {
//            System.err.println(coord + " => " + StringUtils.join("; ", sva.getAnnotation(coord)));
//        }
//        
//        System.err.println("[End sorted]");
//        for(GenomeCoordinates coord: sva.endCoordinates()) {
//            System.err.println(coord + " => " + StringUtils.join("; ", sva.getAnnotation(coord)));
//        }
    }

    @After
    public void tearDown() throws Exception {
//        System.err.println("============================================");
    }

    @Test
    public void testFindAnnotationByPositionBadRef() {
        List<SimpleValueAnnotation> vals;
        vals = sva.findAnnotation("chrX", 100);
        assertEquals("", StringUtils.join(";", vals));
    }
    
    @Test
    public void testFindAnnotationByPosition() {
        List<SimpleValueAnnotation> vals;
        vals = sva.findAnnotation("chr1", 100);
        assertEquals("A", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 300);
        assertEquals("B", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 350);
        Collections.sort(vals);
        assertEquals("B;F", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 520);
        Collections.sort(vals);
        assertEquals("C;F;H", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 680);
        assertEquals(0, vals.size());
    }

    @Test
    public void testFindAnnotationRange() {
        List<SimpleValueAnnotation> vals;
        vals = sva.findAnnotation("chr1", 680, 820);
        assertEquals("D", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 680, 1000);
        Collections.sort(vals);
        assertEquals("D;E", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 150, 250);
        Collections.sort(vals);
        assertEquals("A", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 425, 480);
        Collections.sort(vals);
        assertEquals("F;G;H", StringUtils.join(";", vals));

        vals = sva.findAnnotation("chr1", 820, 850);
        assertEquals(0, vals.size());
    }
    
    @Test
    public void testFindAnnotationStringIntIntStrand() {
        //fail("Not yet implemented"); // TODO
    }

}
