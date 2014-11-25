package org.ngsutils.pileup;

import java.util.ArrayList;
import java.util.List;

import org.ngsutils.support.StringUtils;

public class PileupRecord {
	@Override
	public String toString() {
		return "PileupRecord [ref=" + ref + ", pos=" + pos + ", refBase="
				+ refBase + "]";
	}

	public enum PileupBaseCallOp {
		Match, Del, Ins
	}
	
	public class PileupBaseCall {
		@Override
		public String toString() {
			return op + "(" + call + ", " + qual + ")";
		}

		public final PileupBaseCallOp op;
		public final String call;
		public final int qual;
		
		public PileupBaseCall(PileupBaseCallOp op, String call, int qual) {
			this.op = op;
			this.call = call;
			this.qual = qual;
		}
	}
	public class PileupSampleRecord {
		public final int count;
		public final List<PileupBaseCall> calls;

		public PileupSampleRecord(int count, List<PileupBaseCall> calls) {
			this.count = count;
			this.calls = calls;
		}
	}

	public final String ref;
	public final int pos;
	public final String refBase;
	
	private List<PileupSampleRecord> records = new ArrayList<PileupSampleRecord>();
	
	public static PileupRecord parse(String line) {
//		System.err.println(StringUtils.strip(line));
		String[] cols = StringUtils.strip(line).split("\t");
		
		String ref = cols[0];
		int pos = Integer.parseInt(cols[1]) - 1; // we store this as a 0-based value... Pileup uses 1-based
		String refBase = cols[2];
		
		PileupRecord record = new PileupRecord(ref, pos, refBase);
		
		for (int i=3; i<cols.length; i+=3) {
			int count = Integer.parseInt(cols[i]);
			if (count == 0) {
	            record.addSampleRecord(count, null);
				continue;
			}
			List<PileupBaseCall> calls = new ArrayList<PileupBaseCall>();
			int qual_idx = 0;
			
			for (int j=0; j<cols[i+1].length(); j++) {
				char base = cols[i+1].charAt(j);

				if (base == '^') {
					j++;
				} else if (base == '*' || base == '$') {
					// no op
				} else if (base == '+' || base == '-') {
					String intbuf = "";
					j++;
					while ("0123456789".indexOf(cols[i+1].charAt(j)) > -1) {
						intbuf += cols[i+1].charAt(j);
						j++;
					}
					
					int indelLen = Integer.parseInt(intbuf);
					String indel = cols[i+1].substring(j, j+indelLen);

					if (base == '+') {
						calls.add(record.new PileupBaseCall(PileupBaseCallOp.Ins, indel, -1));
					} else {
						calls.add(record.new PileupBaseCall(PileupBaseCallOp.Del, indel, -1));
					}
					
					j += indelLen-1;
				} else {
					calls.add(record.new PileupBaseCall(PileupBaseCallOp.Match, ""+base, cols[i+2].charAt(qual_idx)-33));
					qual_idx++;
				}
			}
			
			record.addSampleRecord(count, calls);
		}		
			
		return record;
	}

	private void addSampleRecord(int count, List<PileupBaseCall> calls) {
		this.records.add(new PileupSampleRecord(count, calls));		
	}

	public PileupRecord(String ref, int pos, String refBase) {
		this.ref = ref;
		this.pos = pos;
		this.refBase = refBase;
	}

	public int getSampleCount(int sampleNum) {
		if (sampleNum < 0 || sampleNum >= records.size()) {
			return -1;
		}
		return records.get(sampleNum).count;
	}

	public boolean isBlank() {
		return records.size() == 0;
	}

}
