package io.compgen.ngsutils.cli.bed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.fasta.FAIFile;

@Command(name="bed-combine", desc="Given two or more (sorted) BED(3/6) files, combine the BED annotations into one output BED file. This will produce non-overlapping regions (unlike bed-reduce). All other columns (and score) are ignored.", category="bed", experimental=true)
public class BedCombine extends AbstractOutputCommand {
    
    public class MultinameBedRecord implements Comparable<MultinameBedRecord>{
    	private final GenomeSpan coord; 
        private final List<String> names = new ArrayList<String>();
        private final int chainIdx;
        
        public MultinameBedRecord(BedRecord bedRecord, int chainIdx) {
            this.coord = bedRecord.getCoord();
            this.names.add(bedRecord.getName());
            this.chainIdx = chainIdx;
        }
        
        public MultinameBedRecord(GenomeSpan coord, List<String> names, int chainIdx) {
            this.coord = coord;
            this.chainIdx = chainIdx;
            for (String name: names) {
            	this.names.add(name);
            }
        }
        
        @Override
        public int compareTo(MultinameBedRecord o) {
            return coord.compareTo(o.coord);
        }


        public void addName(String name) {
        	if (!this.names.contains(name)) {
        		this.names.add(name);
        	}
        }
        
        public void addName(List<String> names) {
        	for (String name: names) {
        		this.addName(name);
        	}
        }
        
        public String getName() {
        	return getName("|");
        }

        public List<String> getNames() {
        	return Collections.unmodifiableList(names);
        }
        public String getName(String delim) {
        	return StringUtils.join(delim, this.names);
        }
        public GenomeSpan getCoord() {
        	return coord;
        }

        public String getFirstName() {
        	if (this.names.size()>0) {
        		return this.names.get(0);
        	}
        	return null;
        }

    	public MultinameBedRecord clone(GenomeSpan newcoord) {
    		return new MultinameBedRecord(newcoord, this.names, this.chainIdx);
    	}

    }

	
    private String refFilename = null;
    private List<String> bedFilenames = new ArrayList<String>();
    private List<String> names = new ArrayList<String>();
    
    private String defval = null;
    
    private boolean ignoreStrand = false;
    private boolean single = false;
        
    @Option(name="single", desc="Use only one annotation for each position (prioritzed based on arg order)")
    public void setSingle(boolean val) {
        this.single = val;
    }

    @Option(name="bed", desc="BED file to include (order of importance)", allowMultiple=true)
    public void setBed(String bed) {
        this.bedFilenames.add(bed);
        this.names.add(null);
    }

    @Option(name="tag", desc="BED file to include, but use NAME instead of name field.", allowMultiple=true, helpValue="NAME:FILE.bed")
    public void setTag(String bed) throws CommandArgumentException {
    	String[] spl = bed.split(":", 2);
    	if (spl.length!=2) {
            throw new CommandArgumentException("Invalid --tag value!");
    	}
        this.bedFilenames.add(spl[1]);
        this.names.add(spl[0]);
    }

    @Option(name="ref", desc="Reference FASTA Index (FAI file)")
    public void setRef(String ref) {
        this.refFilename = ref;
    }

    @Option(name="default", desc="Default annotation for regions that are unannotated (requires --ref)")
    public void setDefault(String defval) {
        this.defval = defval;
    }

    @Option(name="ns", desc="Ignore strand")
    public void setIgnoreStrand(boolean val) {
        this.ignoreStrand = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (bedFilenames == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        if (refFilename == null && defval != null) {
            throw new CommandArgumentException("--default requires --ref to be set");
        }
        
        FAIFile fai = null;
        if (refFilename != null) {
        	File faifile = new File(refFilename);
        
        	if (!faifile.exists()) {
        		throw new CommandArgumentException("FASTA reference must be indexed!");
        	}
        
        	fai = new FAIFile(refFilename);
        }

        if (ignoreStrand) {
        	processBedFiles(Strand.NONE, fai); // NONE matches PLUS and MINUS
        } else {
        	processBedFiles(Strand.PLUS, fai);
        	processBedFiles(Strand.MINUS, fai);
        }
        
    }

    protected void processBedFiles(Strand strand, FAIFile fai) throws IOException {
//        int counter = 0;

        // key = chrom
    	// list1 = for each input bed file
    	//  list2 = the records for that specific bedfile
        Map<String, List<List<MultinameBedRecord>>> records = new HashMap<String, List<List<MultinameBedRecord>>>();
        List<String> refs = new ArrayList<String>();

        for (int i=0; i<bedFilenames.size(); i++) {
        	Iterator<BedRecord> it = BedReader.readFile(bedFilenames.get(i), ignoreStrand); //ignoreStrand is redundant here...
        	while (it.hasNext()) {
        		BedRecord rec = it.next();
        		if (rec.getCoord().strand.matches(strand)) {
        			// add the records
        			if (!records.containsKey(rec.getCoord().ref)) {
        				refs.add(rec.getCoord().ref);
        				records.put(rec.getCoord().ref, new ArrayList<List<MultinameBedRecord>>());
        			}
        			while (records.get(rec.getCoord().ref).size() <= i) {
        				records.get(rec.getCoord().ref).add(new ArrayList<MultinameBedRecord>());
        			}
        			
        			if (this.names.get(i) != null) {
        				rec = rec.clone(this.names.get(i));
        			}
        			
        			records.get(rec.getCoord().ref).get(i).add(new MultinameBedRecord(rec, i));
        		}
        	}
        }

    	for (String ref: records.keySet()) {
    		for (int i=0; i<records.get(ref).size(); i++) {
    			Collections.sort(records.get(ref).get(i));
    		}
    	}
        
        if (fai!=null) {
        	for (String ref: records.keySet()) {
	        	BedRecord refBed = fai.getBed(ref);
	        	refBed = refBed.clone(defval != null ? defval : ref);
				records.get(ref).add(new ArrayList<MultinameBedRecord>());
				records.get(ref).get(records.get(ref).size()-1).add(new MultinameBedRecord(refBed, records.get(ref).size()-1));
        	}
        	if (defval != null) {
        		names.add(defval);
        	} else {
        		names.add(null);
        	}
        }

        for (String curRef: refs) {
//        	System.out.println("["+curRef+"]");
        	// list of lists...
        	// first -- sources, but priority (which is why we don't merge it all together)
        	// second -- bedrecords for that source... sorted.
        	List<List<MultinameBedRecord>> curRecords = records.get(curRef);
        	MultinameBedRecord first = null;
        	while (true) {
	         	if (first == null) {
		            for (int i=0; i<curRecords.size(); i++)  {
		            	if (curRecords.get(i).size() > 0) {
//		            		System.out.println("["+i+"] " + curRecords.get(i).get(0).getCoord());
		            		MultinameBedRecord rec = curRecords.get(i).get(0); // the lists are sorted...
		        			if (first == null || rec.compareTo(first) < 0) {
		        				first = rec;
		        			}
		            	}
		            }
		            if (first != null) {
			            // remove first from list
		            	curRecords.get(first.chainIdx).remove(0);
		            }
	         	}
	            if (first == null) {
	            	// we are done here...
	            	break;
	            }
            	            	
//        		System.out.println("[first] " + first.getCoord());
            	// find overlaps
            		
        		// look for the next smallest region
        		MultinameBedRecord second = null;
	            for (int i=0; i<curRecords.size(); i++)  {
	            	if (curRecords.get(i).size() > 0) {
//	            		System.out.println("["+i+"] " + curRecords.get(i).get(0).getCoord());
	            		MultinameBedRecord rec = curRecords.get(i).get(0); // the lists are sorted...
	        			if (second == null || rec.compareTo(second) < 0) {
	        				second = rec;
	        			}
	            	}
	            }
//	            if (second != null) {
//	            	System.out.println("[second] " + second.getCoord());
//	            }

	            if (second != null && second.getCoord().overlaps(first.getCoord())) {
//	        		System.out.println("[overlap]");
	            	// merge first and second --
	            	int start1 = first.getCoord().start;
	            	int end1 = first.getCoord().end;
	            	int start2 = second.getCoord().start;
	            	int end2 = second.getCoord().end;
	            	
	            	boolean namematch = true;
	            	for (String name: second.getNames()) {
	            		if (!first.getNames().contains(name)) {
	            			namematch = false;
	            		}
	            	}
	            	
	            	if (namematch) {
	            		//
	            		// A |-------|
	            		// A      |-----|
	            		//
	            		
	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, Math.min(start1,  start2), Math.max(end1,  end2), strand));
	            		first = merged;
	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...

	            		
	            	} else if (start2 > start1 && start2 < end1 && end2 > end1) {
	            		//
	            		// A |-------|
	            		// B      |-----|
	            		//
	            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));

	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end1, strand));
            			merged.addName(second.getNames());

            			MultinameBedRecord right = second.clone(new GenomeSpan(curRef, end1, end2, strand));            			

	            		write(left);            			
            			first = merged;
            			
            			curRecords.get(right.chainIdx).set(0, right); // replace old record with this one.
            			Collections.sort(curRecords.get(right.chainIdx));
            			
    	            } else if (start2 > start1 && end2 < end1) {
	            		//
	            		// A |-------|
	            		// B    |--|
	            		//
	            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));

	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end2, strand));
            			merged.addName(second.getNames());

            			MultinameBedRecord right = first.clone(new GenomeSpan(curRef, end2, end1, strand));            			

	            		write(left);            			
            			first = merged;
            			
	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...

	            		// add flanking to correct chain and resort
            			curRecords.get(right.chainIdx).add(right.chainIdx, right);
            			Collections.sort(curRecords.get(right.chainIdx));
            			
    	            } else if (start2 == start1 && end2 < end1) {
	            		//
	            		// A |-------|
	            		// B |--|
	            		//
	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start1, end2, strand));
            			merged.addName(second.getNames());

            			MultinameBedRecord right = first.clone(new GenomeSpan(curRef, end2, end1, strand));            			

	            		first = merged;
	            		
	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...

	            		// add flanking to correct chain and resort
            			curRecords.get(right.chainIdx).add(0, right); // add this to the first priority chain.
            			Collections.sort(curRecords.get(right.chainIdx));
            			
		            } else if (start2 > start1 && end2 == end1) {
	            		//
	            		// A |-------|
	            		// B      |--|
	            		//
		            	
	            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));

	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end2, strand));
	        			merged.addName(second.getNames());

	        			curRecords.get(second.chainIdx).remove(0); // remove the second from list...
		
	            		write(left);            			
	            		first = merged;
	        			
//		            } else if (start2 > start1 && start2 < end1 && end2 > end1) {
//	            		//
//	            		// A |-------|
//	            		// B      |-----|
//	            		//
//		            	
//	            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));
//
//	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end1, strand));
//	        			merged.addName(second.getNames());
//		
//            			MultinameBedRecord right = second.clone(new GenomeSpan(curRef, end1, end2, strand));            			
//	        			
//	            		write(left);            			
//	            		first = merged;
//
//	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...
//
//	            		curRecords.get(right.chainIdx).add(0, right); // add this to the first priority chain.
//            			Collections.sort(curRecords.get(right.chainIdx));
	        			
		            } else if (start2 == start1 && start2 < end1 && end2 > end1) {
	            		//
	            		// A |-------|
	            		// B |-----------|
	            		//
		            	
	            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start1, end1, strand));
	        			merged.addName(second.getNames());
		
            			MultinameBedRecord right = second.clone(new GenomeSpan(curRef, end1, end2, strand));            			
	        			
	            		first = merged;

	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...

	            	
	            		curRecords.get(right.chainIdx).add(0, right); // add this to the first priority chain.
            			Collections.sort(curRecords.get(right.chainIdx));

		            } else if (start2 == start1 && end2 == end1) {
	            		//
	            		// A |-------|
	            		// B |-------|
	            		//

		            	first.addName(second.getNames());
	        			

	            		curRecords.get(second.chainIdx).remove(0); // remove the second from list...
	        			

		            } else {
	            		System.out.println("first/second odd overlap");
	            		System.out.println("first: " + first.getCoord());
	            		System.out.println("second: " + second.getCoord());
	            		System.exit(1);
		            }
	            	
	            } else {
	            	write(first);
	            	first = null;
	            }
        	}
        }
    }
    	            
            		
            		
            		
            		
        		
//        		
//        		
//        		
//        		
//        		
//	         	int firstIdx = -1;
//	        	@SuppressWarnings("unused")
//				int secondIdx = -1;
//	        	BedRecord first = null;
//	        	BedRecord second = null;
//	        	
//	        	
//	        	
//	            for (int i=0; i<curRecords.size(); i++)  {
//	            	if (curRecords.get(i).size() > 0) {
//	            		System.out.println("["+i+"] " + curRecords.get(i).get(0).getCoord());
//	        			BedRecord rec = curRecords.get(i).get(0); // the lists are sorted...
//	        			if (first == null || rec.compareTo(first) < 0) {
//	        				if (first != null) {
//	        					second = first;
//	        					secondIdx = firstIdx;
//	        				}
//	        				first = rec;
//	        				firstIdx = i;
//	        			} else if (second == null || rec.compareTo(second) < 0) {
//	        				second = rec;
//	        				secondIdx = i;
//	            		}
//	            	}
//	            }
//	
//	            if (first == null && second == null) {
//	            	break;
//	            }
//	            
//	            System.out.println("1 ["+firstIdx+"]" + first + " " + first.getCoord());
//	            if (second != null) {
//	            	System.out.println("2 ["+secondIdx+"]" + second + " " + second.getCoord());
//	            }
//	            GenomeSpan newspan;
//	            int start, end;
//	            if (second!=null && first.getCoord().overlaps(second.getCoord())) {
//	            	// overlap, so find start/end and update
//	            	start = first.getCoord().start;
//	            	end = second.getCoord().start;
//	            	
//	            	if (start == end) {
//	            		if (first.getCoord().end < second.getCoord().end) {
//	            			end = first.getCoord().end;
//	            		} else {
//	            			end = second.getCoord().end;
//	            		}
//	            	}
//	            	
//	            	newspan = new GenomeSpan(curRef, start, end, strand);
////	            	System.out.println(newspan);
//	            } else {
//	            	newspan = first.getCoord();
//	            	start = first.getCoord().start;
//	            	end = first.getCoord().start;
//	            }
//            	// find all overlapping records
//            	List<String> newnames = new ArrayList<String>();
//            	for (int i=0; i< curRecords.size(); i++) {
//            		if (curRecords.get(i).size()>0 && curRecords.get(i).get(0).getCoord().overlaps(newspan)) {
//            			BedRecord match = curRecords.get(i).get(0);
//            			if (names.get(i) == null) {
//            				newnames.add(match.getName());
//            			} else {
//            				newnames.add(names.get(i));
//            			}
//            			
//            			if (match.getCoord().end == end) {
//            				curRecords.get(i).remove(0);
//            			} else {
//	            			// update these spans
//	            			GenomeSpan newcoord = new GenomeSpan(curRef, end, match.getCoord().end, match.getCoord().strand);
//	            			BedRecord newRec = match.clone(newcoord);
//	            			curRecords.get(i).set(0,  newRec);
//            			}
//            		}
//	            
//            	
//	        		BedRecord newrec;
//	        		if (single) {
//	        			newrec = new BedRecord(newspan, newnames.get(0));
//	        		} else {
//	        			newrec = new BedRecord(newspan, StringUtils.join("|", newnames));
//	        		}
//	            	
//	        		write(newrec);
//            	}
////	            	
////	            } else {
////	            	// no overlap, write the first and remove it from tree
////	            	write(first, names.get(firstIdx));
////	            	curRecords.get(firstIdx).remove(0);
////	            }
//	            
////	            if (counter++ > 100) {            
////	            	return;
////	            }
//        	}
//        }   
//    }


	private void write(MultinameBedRecord rec) throws IOException {
        List<String> outs = new ArrayList<String>();
        outs.add(rec.getCoord().ref);
        outs.add(""+rec.getCoord().start);
        outs.add(""+rec.getCoord().end);
        
        if (names != null) {
        	if (single) {
        		outs.add(rec.getFirstName());
        	} else {
            outs.add(rec.getName());
        	}
            if (!ignoreStrand) {
                outs.add(""); // ignore score
                
                if (rec.getCoord().strand != Strand.NONE) {
                    outs.add(rec.getCoord().strand.toString());
                } else {
                    // always need to output a valid strand...
                    outs.add(Strand.PLUS.toString());
                }
            }
        }
        out.write((StringUtils.join("\t", outs) + "\n").getBytes());
    }
}


