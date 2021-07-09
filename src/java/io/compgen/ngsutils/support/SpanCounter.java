package io.compgen.ngsutils.support;

import java.util.ArrayList;
import java.util.List;

/**
 * This counter will count events across a genome span. For example, the depth of reads across the genome.
 * This is meant to be a sliding window, so that events are only tracked across the window. Once we are
 * past the window, it's expected that head nodes will be removed.
 * 
 * This is implemented as a pair of arrays.
 * 
 * @author mbreese
 *
 */

public class SpanCounter {

	public static final int DEFAULT_BUFFERSIZE = 100000;
	
	public class PosCount {
		public final long pos;
		public final int count;
		
		public PosCount(long pos, int count) {
			this.pos = pos;
			this.count = count;
		}
	}
	
	public class BufferPos {
		public final long start;
		public final long end;
		public final int[] counts;
		private BufferPos next = null;
		
		public BufferPos(long start, int bufSize) {
			this.start = start;
			this.end = start + bufSize;
			this.counts = new int[bufSize];
		}

		public BufferPos next() {
			if (next == null) {
//				System.err.println("Adding new buffer link");
				next = new BufferPos(start + counts.length, counts.length);
			}
			return next;
		}
	}
	
	private BufferPos head;
	private long curpos = 0;
	private long maxpos = 0;

	public SpanCounter() {
		this(DEFAULT_BUFFERSIZE);
	}
	
	public SpanCounter(int bufSize) {
		this.head = new BufferPos(0, bufSize);
	}
	
	/**
	 * Add span to the counter. Spans do not have to be sorted, but it is significantly more memory efficient if they are.
	 * You can not add a span that is earlier than curpos (which is the output position)
	 * @param start - zero based
	 * @param end
	 * @throws Exception
	 */
	public void incr(long start, long end) throws Exception {
		if (start < curpos) {
			throw new Exception("Past counter position. Pointer: "+curpos + ", start="+start + ", unsorted input?");
		}
//		System.err.println("incr("+start+","+end+")");
		BufferPos curBuf = head;
		
		for (long i=start; i<end; i++) {
			while (i >= curBuf.end) {
				curBuf = curBuf.next();
			}
			curBuf.counts[(int)(i - curBuf.start)]++;
		}
		
		if (maxpos < end) {
			maxpos = end;
		}
	}	

	/**
	 * Get the counts for a given position. This will move the current pointer
	 * and the next getCount will be for curpos + 1
	 * @return the count for the curpos (zero-based!)
	 */
	public PosCount pop() {
		PosCount ret = new PosCount(curpos, head.counts[(int)(curpos - head.start)]);
		curpos++;
		
		if (curpos - head.start >= head.counts.length) {
			head = head.next();
		}

		return ret;
	}

	/**
	 * Return the counts upto (but not including) the limit 
	 * @param limit - the upper limit of positions to include (exclusive). So, if you pass a limit of 100, you'll get the counts of 0-99.
	 * @return
	 */
	public List<PosCount> pop(int limit) {
		List<PosCount> ret = new ArrayList<PosCount>();
		while (curpos < limit) {
			ret.add(pop());
		}
		
		return ret;
	}

	public long getMaxPos() {
		return maxpos;
	}
	
	public long getCurPos() {
		return curpos;
	}
	
}