package io.compgen.ngsutils.cli.fastq;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.compgen.ngsutils.fastq.FastqRead;

class FastqOverlapTest {

	@Test
	void testFindOverlapRead() {
    	FastqRead one = new FastqRead("test_one", "AAAAAAAAAAAACGT", "123456789012345");
    	FastqRead two = new FastqRead("test_one", "AAAAAAAAAAAAAAA", "123456789012345");
    	FastqRead tre = new FastqRead("test_one", "ACGTAAAAAAAAAAA", "123456789012345");
    	FastqRead qua = new FastqRead("test_one", "TTTGCACCCCCCCCC", "abcdefghijklmno");
    	
    	FastqRead over1 = FastqOverlap.findOverlapRead(one,  one,  5);
    	assertNull(over1);

    	FastqRead over2 = FastqOverlap.findOverlapRead(one,  two,  5);
    	assertNull(over2);
    	
    	FastqRead over3 = FastqOverlap.findOverlapRead(one,  tre,  5);
    	assertNull(over3);
    	
    	FastqRead over4 = FastqOverlap.findOverlapRead(one,  qua,  5);
    	assertNotNull(over4);
    	assertEquals(over4.getSeq(),  "AAAAAAAAAAAACGTGGGGGGGGG");
    	assertEquals(over4.getQual(), "123456789012345ghijklmno");
	}

}
