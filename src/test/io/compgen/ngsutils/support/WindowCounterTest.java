package io.compgen.ngsutils.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WindowCounterTest {

	@Test
	void testWindowCounter() {
		WindowCounter counter = new WindowCounter();
		assertNull(counter.head());
		
		counter.incr(10000);
		assertNotNull(counter.head);
		assertEquals(counter.head().one, 10000);
		assertEquals(counter.head().two, 1);

		counter.incr(10000);
		assertEquals(counter.head().two, 2);

		counter.incr(10001);
		counter.incr(10002);
		counter.incr(10001);
		counter.incr(10002);
		counter.incr(10002);
	
		assertEquals(counter.size(), 3);
		
		assertEquals(counter.head.next.pos, 10001);
		assertEquals(counter.head.next.next.pos, 10002);

		assertEquals(counter.head.next.value, 2);
		assertEquals(counter.head.next.next.value, 3);

		counter.pop();
		
		assertEquals(counter.head.pos, 10001);
		assertEquals(counter.head.next.pos, 10002);

		assertEquals(counter.head.value, 2);
		assertEquals(counter.head.next.value, 3);

		assertEquals(counter.size(), 2);

		counter.incr(10002);
		counter.incr(9999);

		assertEquals(counter.head.pos, 9999);
		assertEquals(counter.head.next.pos, 10001);

		assertEquals(counter.head.value, 1);
		assertEquals(counter.head.next.value, 2);
		assertEquals(counter.head.next.next.value, 4);

		assertEquals(counter.size(), 3);

	}

}
