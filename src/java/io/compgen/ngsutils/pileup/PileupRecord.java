package io.compgen.ngsutils.pileup;

import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;

public class PileupRecord {
	@Override
	public String toString() {
		return "PileupRecord [ref=" + ref + ", pos=" + pos + ", refBase="
				+ refBase + "]";
	}

	public enum PileupBaseCallOp {
		Match, // Match, mismatch, eq, variant (M=X) 
		Del,   // D
		Ins,   // I
		Gap    // N (> or <)
	}
	
	public class PileupBaseCall {
        public boolean matches(String q) {
            if (q.charAt(0) == '+') { 
                if (op != PileupBaseCallOp.Ins) {
                    return false;
                }
                q = q.substring(1);
            }
            if (q.charAt(0) == '-') { 
                if (op != PileupBaseCallOp.Del) {
                    return false;
                }
                q = q.substring(1);
            }

            if (q.charAt(0) == '<' || q.charAt(0) == '>') { 
                if (op != PileupBaseCallOp.Ins) {
                    return false;
                }
                q = q.substring(1);
            }

            
            if (!call.equals(q)) {
                return false;
            }
            return true;
        }
		@Override
		public String toString() {
            if (op == PileupBaseCallOp.Ins) {
                return "+"+call;
            }
            if (op == PileupBaseCallOp.Del) {
                return "-"+call;
            }
            if (op == PileupBaseCallOp.Gap) {
                return plusStrand ? ">": "<";  // not sure which is which
            }
            return call;
		}

		public final PileupBaseCallOp op;
		public final String call;
		public final int qual;
		public final boolean plusStrand;
		public final int readPos; // Note: this is always relative to the + strand position of the read, regardless of if the read is on the plus or minus strand.
		                          //       Will be "-1" if missing from file
		
		public PileupBaseCall(PileupBaseCallOp op, String call, int qual, int readPos) {
		    this(op, call, qual, "", readPos);
		}

        public PileupBaseCall(PileupBaseCallOp op, String call, int qual, String refBase, int readPos) {
            this.op = op;
            this.qual = qual;
            this.readPos = readPos;
            
            if (op == PileupBaseCallOp.Match) {
                if (call.equals(".")) {
                    this.call = refBase;
                    this.plusStrand = true;
                } else if (call.equals(",")) {
                    this.call = refBase;
                    this.plusStrand = false;
                } else {
                    this.call = call.toUpperCase();
                    if (call.toUpperCase().equals(call)) {
                        this.plusStrand = true;
                    } else {
                        this.plusStrand = false;
                    }                    
                }
            } else if (op == PileupBaseCallOp.Gap) {
                this.call = "";
                this.plusStrand = call.equals(">");
            } else {
                this.call = call.toUpperCase();
                if (call.toUpperCase().equals(call)) {
                    this.plusStrand = true;
                } else {
                    this.plusStrand = false;
                }
            }
        }
	}
	
	public class PileupSampleRecord {
		public final int coverage;
		public final List<PileupBaseCall> calls;

		public PileupSampleRecord(int coverage, List<PileupBaseCall> calls) {
			this.coverage = coverage;
			this.calls = calls;
		}
	}

	public final String ref;
	public final int pos;
	public final String refBase;
	
	private List<PileupSampleRecord> records = new ArrayList<PileupSampleRecord>();
	
    public static PileupRecord parse(String line) {
        return parse(line, 0, false);
    }
    public static PileupRecord parse(String line, int minBaseQual, boolean nogaps) {
//		System.err.println(StringUtils.strip(line));
		String[] cols = StringUtils.strip(line).split("\t");
		
		// check to see if there are two or three columns per sample
		// if there are three, the third is the read position.
		// if there are two, we don't know the read positions...
		
		boolean hasReadPos = cols.length >=7 && (cols.length-3) % 4 == 0;
		
		for (int i=3; hasReadPos && i<cols.length; i+=4) {
		    try {
		        Integer.parseInt(cols[i]);
		    } catch (NumberFormatException e) {
		        hasReadPos = false;
		    }
		}
		
		int offset = 4;
		
		if (!hasReadPos) {
		    offset = 3;
		}
		
		
		String ref = cols[0];
		int pos = Integer.parseInt(cols[1]) - 1; // we store this as a 0-based value... Pileup uses 1-based
		String refBase = cols[2].toUpperCase();
		
		PileupRecord record = new PileupRecord(ref, pos, refBase);
		
		for (int i=3; i<cols.length; i+=offset) {
			int coverage = Integer.parseInt(cols[i]);
			if (coverage == 0) {
	            record.addSampleRecord(coverage, null);
				continue;
			}
			List<PileupBaseCall> calls = new ArrayList<PileupBaseCall>();
			int qual_idx = 0;
			
			int[] readPos = null;
			if (hasReadPos) {
    			String[] readPosSpl = cols[i+3].split(",");
    			readPos = new int[readPosSpl.length];
    			for (int j=0; j<readPosSpl.length; j++) {
    			    readPos[j] = Integer.parseInt(readPosSpl[j]);
    			}
			}
			
			for (int j=0; j<cols[i+1].length(); j++) {
				char base = cols[i+1].charAt(j);

				if (base == '^') {
					j++; // skip the mapping quality for the read?
				} else if (base == '$') {
				    // no op, end of a read.
				} else if (base == '*') {
				    // * is a deletion (in a padded sequence - unsure about what this means...)
				    // htslib:  @field  is_del     1 iff the base on the padded read is a deletion
				    // The deletion was indicated in a prior line??? 
				    // (-1A in one line, * in the next to keep order of reads fixed?)
				     
				    // the read pos is set for this, but not otherwise needed
                    qual_idx++;
				    
				} else if (base == '+' || base == '-') {
					String intbuf = "";
					j++;
					while ("0123456789".indexOf(cols[i+1].charAt(j)) > -1) {
						intbuf += cols[i+1].charAt(j);
						j++;
					}
					
					int indelLen = Integer.parseInt(intbuf);
					String indel = cols[i+1].substring(j, j+indelLen);

					// always add indels (no good qual scores avail)
					if (base == '+') {
						calls.add(record.new PileupBaseCall(PileupBaseCallOp.Ins, indel, -1, hasReadPos ? readPos[qual_idx-1] : -1));
					} else {
						calls.add(record.new PileupBaseCall(PileupBaseCallOp.Del, indel, -1, hasReadPos ? readPos[qual_idx-1] : -1));
					}
					
					j += indelLen-1;
                } else if (base == '>' || base == '<') {
                    // < or > is a "refskip" (N CIGAR op)
                    if (cols[i+2].charAt(qual_idx)-33 > minBaseQual) {
                        if (!nogaps) {
                            calls.add(record.new PileupBaseCall(PileupBaseCallOp.Gap, ""+base, cols[i+2].charAt(qual_idx)-33, refBase, hasReadPos ? readPos[qual_idx] : -1));
                        }
                    }
                    qual_idx++;
				} else {
				    if (cols[i+2].charAt(qual_idx)-33 >= minBaseQual) {
				        calls.add(record.new PileupBaseCall(PileupBaseCallOp.Match, ""+base, cols[i+2].charAt(qual_idx)-33, refBase, hasReadPos ? readPos[qual_idx] : -1));
				    }
					qual_idx++;
				}
			}
			
			record.addSampleRecord(coverage, calls);
		}		
			
		return record;
	}

	private void addSampleRecord(int coverage, List<PileupBaseCall> calls) {
		this.records.add(new PileupSampleRecord(coverage, calls));		
	}

	public PileupRecord(String ref, int pos, String refBase) {
		this.ref = ref;
		this.pos = pos;
		this.refBase = refBase;
	}

	public int getSampleDepth(int sampleNum) {
		if (sampleNum < 0 || sampleNum >= records.size()) {
			return -1;
		}
		return records.get(sampleNum).coverage;
	}

	public int getNumSamples() {
		return records.size();
	}

	public PileupSampleRecord getSampleRecords(int sampleNum) {
	    return records.get(sampleNum);
	}
	
	public boolean isBlank() {
		return records.size() == 0;
	}

}
