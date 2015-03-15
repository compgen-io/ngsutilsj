package io.compgen.ngsutils.pileup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SlidingWindowPileup {
	public final String ref;
	public final int start;
	public final int end;
	public final int[] normalCounts;
	public final int[] tumorCounts;
	
	public SlidingWindowPileup(String ref, int start, int end, int[] normalCounts, int[] tumorCounts) {
		this.ref = ref;
		this.start = start;
		this.end = end;
		this.normalCounts = normalCounts;
		this.tumorCounts = tumorCounts;
	}

	public static Iterable<SlidingWindowPileup> readMPileup(final PileupReader reader, final int windowSize, final int stepSize) {
		return new Iterable<SlidingWindowPileup>(){
			@Override
			public Iterator<SlidingWindowPileup> iterator() {
				return new Iterator<SlidingWindowPileup> (){
					private SlidingWindowPileup window = null;
					private String currentRef = null;
					private int windowStart = -1;

					Iterator<PileupRecord> it = reader.iterator();					
					List<PileupRecord> buffer = new LinkedList<PileupRecord>();
					
					@Override
					public boolean hasNext() {
						if (window != null) {
							return true;
						}
						
						PileupRecord current = null;
						if (buffer.size()==0) {
							if (!it.hasNext()) {
								return false;
							}
							current = it.next();
							buffer.add(current);
							currentRef = current.ref;
							windowStart = 0;
						} else {
							currentRef = buffer.get(0).ref;
						}
						
						int windowEnd = windowStart + windowSize;

						while (current == null || (current.pos < windowEnd && current.ref.equals(currentRef))) {
							if (!it.hasNext()) {
								current = null;
								break;
							}
							current = it.next();
							buffer.add(current);
						}
						
						List<Integer> normalAcc = new ArrayList<Integer>();
						List<Integer> tumorAcc = new ArrayList<Integer>();
						
						int currentPos = windowStart;
						
						for (PileupRecord record: buffer) {
							if (record.pos >= windowEnd || !record.ref.equals(currentRef)) {
								break;
							}

							if (record.pos > currentPos + 1) {
								normalAcc.add(0);
								tumorAcc.add(0);
								currentPos ++;
							}
							
							normalAcc.add(record.getSampleCount(0));
							tumorAcc.add(record.getSampleCount(1));
							currentPos = record.pos;
						}

						while (currentPos+1 < windowEnd) {
							normalAcc.add(0);
							tumorAcc.add(0);
							currentPos ++;
						}

						window = new SlidingWindowPileup(currentRef, windowStart, windowEnd, convertIntList(normalAcc), convertIntList(tumorAcc));
						windowStart += stepSize;

						while (buffer.size() > 0 && buffer.get(0).ref.equals(currentRef) && buffer.get(0).pos < windowStart) {
							buffer.remove(0);
						}
						
						return true;
						
					}

					@Override
					public SlidingWindowPileup next() {
						SlidingWindowPileup cur = window;
						window = null;
						return cur;
					}

					@Override
					public void remove() {
					}};
			}};
	}
	public static int[] convertIntList(List<Integer> list) {
		int[] out = new int[list.size()];
		for (int i=0; i< list.size(); i++) {
			out[i] = list.get(i);
		}
		return out;
	}
}
