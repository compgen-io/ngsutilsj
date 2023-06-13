package io.compgen.ngsutils.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.compgen.ngsutils.support.SpanCounter.PosCount;

class SpanCounterTest {

	@Test
	void testWindowCounter() throws Exception {
		SpanCounter counter = new SpanCounter(5);
		
		counter.incr(0,10);
		counter.incr(1,11);
		counter.incr(2,12);

		assertEquals(counter.getCurPos(), 0);
		assertEquals(counter.getMaxPos(), 12);

		
		counter.incr(21,31);

		assertEquals(counter.getMaxPos(), 31);

		PosCount c1 = counter.pop();
		assertEquals(c1.pos, 0);
		assertEquals(c1.count, 1);

		assertEquals(counter.getCurPos(), 1);

		
		PosCount c2 = counter.pop();
		assertEquals(c2.pos, 1);
		assertEquals(c2.count, 2);

		PosCount c3 = counter.pop();
		assertEquals(c3.pos, 2);
		assertEquals(c3.count, 3);

		PosCount c4 = counter.pop();
		assertEquals(c4.pos, 3);
		assertEquals(c4.count, 3);

		int i = 4;
		List<PosCount> cl = counter.pop(10);
		for (PosCount c: cl) {
			assertEquals(c.pos, i);
			assertEquals(c.count, 3);
			i++;
		}

		PosCount c10 = counter.pop();
		assertEquals(c10.pos, 10);
		assertEquals(c10.count, 2);

		PosCount c11 = counter.pop();
		assertEquals(c11.pos, 11);
		assertEquals(c11.count, 1);

		PosCount c12 = counter.pop();
		assertEquals(c12.pos, 12);
		assertEquals(c12.count, 0);

		PosCount c13 = counter.pop();

		assertEquals(c13.pos, 13);
		assertEquals(c13.count, 0);

		PosCount tmp = counter.pop();
		while (tmp.count == 0) {
			tmp = counter.pop();
		}

		assertEquals(tmp.pos, 21);
		assertEquals(tmp.count, 1);

		
		
		
	}

}
