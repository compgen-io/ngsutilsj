package io.compgen.ngsutils.cli.bed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
import io.compgen.ngsutils.bed.BedStrandFilter;
import io.compgen.ngsutils.fasta.FAIFile;
import io.compgen.ngsutils.support.BufferedIterator;
import io.compgen.ngsutils.support.BufferedIteratorImpl;
import io.compgen.ngsutils.support.ListSortedBufferedIterator;

@Command(name="bed-merge", desc="Given two or more (sorted) BED(3/6) files, combine the BED annotations into one output BED file. This can produce non-overlapping regions (--split) or unions (like bed-reduce). All other columns (and score) are ignored. Because this requires sorted inputs, it is more effcient than bed-reduce.", category="bed", experimental=true)
public class BedMerge extends AbstractOutputCommand {
    
	public class MultinameIterator implements Iterator<MultinameBedRecord> {
		private Iterator<BedRecord> it;
		public MultinameIterator(Iterator<BedRecord> it) {
			this.it = it;
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public MultinameBedRecord next() {
			BedRecord rec = it.next();
			return new MultinameBedRecord(rec);
		}
	}
	
    public class MultinameBedRecord implements Comparable<MultinameBedRecord>{
    	private final GenomeSpan coord; 
        private final SortedSet<String> names = new TreeSet<String>();
        
        public MultinameBedRecord(BedRecord bedRecord) {
            this.coord = bedRecord.getCoord();
            this.names.add(bedRecord.getName());
        }
        
        public MultinameBedRecord(GenomeSpan coord, Set<String> names) {
            this.coord = coord;
            for (String name: names) {
            	this.names.add(name);
            }
        }
        
        @Override
        public int compareTo(MultinameBedRecord o) {
            return coord.compareTo(o.coord);
        }


        public void addName(String name) {
    		this.names.add(name);
        }
        
        public void addName(Collection<String> names) {
        	for (String name: names) {
        		this.addName(name);
        	}
        }
        
        public Collection<String> getNames() {
        	return Collections.unmodifiableCollection(names);
        }
        public String getName(String delim) {
        	return StringUtils.join(delim, this.names);
        }
        public GenomeSpan getCoord() {
        	return coord;
        }

        public String getFirstName() {
        	if (this.names.size()>0) {
        		return this.names.first();
        	}
        	return null;
        }

    	public MultinameBedRecord clone(GenomeSpan newcoord) {
    		return new MultinameBedRecord(newcoord, this.names);
    	}

    }

	
    private String refFilename = null;
    private List<String> bedFilenames = new ArrayList<String>();
    private List<String> names = new ArrayList<String>();
    
    private String defval = null;
    private String delim = "|";
    
    private boolean ignoreStrand = false;
    private boolean single = false;
    private boolean split = false;
        
    @Option(name="single", desc="Use only one annotation for each position (prioritzed based on arg order)")
    public void setSingle(boolean val) {
        this.single = val;
    }

    @Option(name="delim", desc="Delimited character used for combining name fields", defaultValue="|")
    public void setDelim(String delim) {
    	this.delim = delim;
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

    @Option(name="default", desc="Default annotation for regions that are missing (requires --ref)")
    public void setDefault(String defval) {
        this.defval = defval;
    }

    @Option(name="ns", desc="Ignore strand")
    public void setIgnoreStrand(boolean val) {
        this.ignoreStrand = val;
    }

    @Option(name="split", desc="Split overlapping regions")
    public void setSplit(boolean val) {
        this.split = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (bedFilenames == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        if (refFilename == null && defval != null) {
            throw new CommandArgumentException("--default requires --ref to be set");
        }
        if (refFilename != null && defval == null) {
        	defval = "missing";
        }

        FAIFile fai = null;
        
        if (refFilename != null) {
        	File faifile = new File(refFilename);
        
        	if (!faifile.exists()) {
        		throw new CommandArgumentException("FASTA reference must be indexed!");
        	}
        
        	fai = new FAIFile(refFilename);
        }

        Iterator<BedRecord> it = BedReader.readFile(bedFilenames.get(0), false);
        
        if (!it.hasNext()) {
            throw new CommandArgumentException("Invalid BED file: "+ bedFilenames.get(0));
        }
        
        // check for stranded BED file -- we will reuse this record.
    	BedRecord rec = it.next();
        if (rec.getCoord().strand == Strand.NONE) {
        	System.err.println("Non-stranded BED file detected. Ignoring all strand comparisons.");
        	ignoreStrand = true;
        }
        
		if (ignoreStrand) {
			processBedFiles(Strand.NONE, fai, it, rec); // NONE matches PLUS and MINUS
		} else {
	    	if (rec.getCoord().strand.matches(Strand.PLUS)) {
	    		processBedFiles(Strand.PLUS, fai, it, rec);
				processBedFiles(Strand.MINUS, fai, null, null);
	    	} else if (rec.getCoord().strand.matches(Strand.MINUS)) {
	    		processBedFiles(Strand.PLUS, fai, it, null);
				processBedFiles(Strand.MINUS, fai, null, rec);
	    	} else {
	    		// this shouldn't happen
	    	}
	    	
		}
    }    
    
    protected void processBedFiles(Strand strand, FAIFile fai) throws IOException {
    	processBedFiles(strand, fai, null, null);
    }
    
    protected void processBedFiles(Strand strand, FAIFile fai, Iterator<BedRecord> firstIterator, BedRecord firstRecord) throws IOException {
        List<BufferedIterator<MultinameBedRecord>> srcBeds = new ArrayList<BufferedIterator<MultinameBedRecord>>(); 
        ListSortedBufferedIterator<MultinameBedRecord> bufList = new ListSortedBufferedIterator<MultinameBedRecord>();

    	if (firstRecord.getCoord().strand.matches(strand)) {
    		bufList.add(new MultinameBedRecord(firstRecord));
    	}

        for (int i=0; i<bedFilenames.size(); i++) {
        	if (i==0 && firstIterator != null) {
            	srcBeds.add(new BufferedIteratorImpl<MultinameBedRecord>(
            			// Convert BedRecord to Multiname records
            			new MultinameIterator(
            					// Only pull records matching the strand
            					new BedStrandFilter(
            							// primary bed reader
            							firstIterator, 
            							strand))));
        	} else {
	        	// Buffer the iterator (so we can peek())
	        	srcBeds.add(new BufferedIteratorImpl<MultinameBedRecord>(
	        			// Convert BedRecord to Multiname records
	        			new MultinameIterator(
	        					// Only pull records matching the strand
	        					new BedStrandFilter(
	        							// primary bed reader
	        							BedReader.readFile(bedFilenames.get(i), false), 
	        							strand))));
        	}
        }        

        // This is a catch-all that we can add directly to (and it will re-sort), so we can add new
        // merged regions or FAI regions on the fly.
        srcBeds.add(bufList);        
        
        MultinameBedRecord first = null;
        String curRef = null;
        
        boolean found = true;
        
        while (found) {
        	if (first == null) {
        		// we don't have a current record, so let's get one.
        		
        		MultinameBedRecord low = null;
                int lowIdx = -1;        
                
                boolean refMatch = false;
                boolean checked = false;
                
		        for (int i=0;i<srcBeds.size();i++) {
		        	MultinameBedRecord rec = srcBeds.get(i).peek();		        	
		        	if (rec == null) {
		        		continue;
		        	}
		        	checked = true;
		        	
		        	if (curRef == null) {
		        		curRef = rec.getCoord().ref;
		        		if (verbose) {
		        			System.err.println(curRef);
		        		}
		        		if (fai != null) {
		        			// we don't necessarily have the same sort order between FASTA/FAI and BED,
		        			// so, we'll pull the BedRecord when we get the new curRef
		        			//
		        			// the bufList should be the last srcBed polled, so this should be the first
		        			// record
		        			bufList.add(new MultinameBedRecord(fai.getBed(curRef)));
		        		}
		        	} else if (!curRef.equals(rec.getCoord().ref)) {
		    			continue;
		        	}

		        	refMatch = true;
		        	
		        	if (low == null || rec.getCoord().start < low.getCoord().start) {
		        		low = rec;
		        		lowIdx = i;
		        	}
		        }
		        if (!refMatch && checked) {
		        	// we don't have a current record, and none of the inputs matched the curRef
		        	// we must have exhausted this ref, so let's go to the next one.		        	
		        	curRef = null;
		        	continue;
		        }
		        if (low != null) {
		        	first = low;
		        	srcBeds.get(lowIdx).next(); // remove the low record
		        }
        	}
        	if (first == null) {
        		// no current record, so we must be done here...
        		System.err.println("Done!");
        		break;
        	}
        	
        	// we have a current record, so let's find the next lowest to look for an overlap.
        	MultinameBedRecord second = null;
            int secondIdx = -1;        

	        for (int i=0;i<srcBeds.size();i++) {
	        	MultinameBedRecord rec = srcBeds.get(i).peek();
	        	if (rec == null) {
	        		continue;
	        	}
	        	
	        	if (curRef == null) {
	        		curRef = rec.getCoord().ref;
	        	} else if (!curRef.equals(rec.getCoord().ref)) {
	    			continue;
	        	}

	        	if (second == null || rec.getCoord().start < second.getCoord().start) {
	        		second = rec;
	        		secondIdx = i;
	        	}
	        }

	        if (second == null || !second.getCoord().overlaps(first.getCoord())) {
	        	// no second record for the chrom/ref... or the second doesn't overlap
	        	// write the current one out, and look for a new current
	        	write(first);
	        	first = null;
	        	continue;
	        } else {
	        	// remove the second value from it's main buffer.
	        	srcBeds.get(secondIdx).next();
	        
            	// merge first and second --
            	int start1 = first.getCoord().start;
            	int end1 = first.getCoord().end;
            	int start2 = second.getCoord().start;
            	int end2 = second.getCoord().end;
            	
            	boolean namematch = true;
            	for (String name: second.getNames()) {
            		if (!first.getNames().contains(name)) {
            			namematch = false;
            			break;
            		}
            	}
            	if (namematch) {
	            	for (String name: first.getNames()) {
	            		if (!second.getNames().contains(name)) {
	            			namematch = false;
	            			break;
	            		}
	            	}
            	}
            	
            	if (namematch || !split) {
            		//
            		// A |-------|
            		// A      |-----|
            		//
            		// *or*
            		//
            		// A |-------|
            		// B      |-----|
            		// B  |-----|
            		//
            		// and not splitting...
            		//
            		
            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, Math.min(start1,  start2), Math.max(end1,  end2), strand));
            		if (!split && !namematch) {
            			merged.addName(second.getNames());
            		}
            		first = merged;
            		
            	} else if (start2 > start1 && start2 < end1 && end2 > end1) {
            		//
            		// A |-------|
            		// B      |-----|
            		//   LLLLLmmmmRRR
            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));
            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end1, strand));
        			merged.addName(second.getNames());

        			MultinameBedRecord right = second.clone(new GenomeSpan(curRef, end1, end2, strand));          			

            		write(left);            			
        			first = merged;
        			bufList.add(right);
        			        			
	            } else if (start2 > start1 && end2 < end1) {
            		//
            		// A |-------|
            		// B    |--|
            		//   LLLmmmmRR
            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));

            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end2, strand));
        			merged.addName(second.getNames());

        			MultinameBedRecord right = first.clone(new GenomeSpan(curRef, end2, end1, strand));            			

            		write(left);            			
        			first = merged;
        			bufList.add(right);
        			
	            } else if (start2 == start1 && end2 < end1) {
            		//
            		// A |-------|
            		// B |--|
            		//   mmmmRRRRR
            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start1, end2, strand));
        			merged.addName(second.getNames());

        			MultinameBedRecord right = first.clone(new GenomeSpan(curRef, end2, end1, strand));            			

            		first = merged;
            		bufList.add(right);
            		        			
	            } else if (start2 > start1 && end2 == end1) {
            		//
            		// A |-------|
            		// B      |--|
            		//   LLLLLmmmm
	            	
            		MultinameBedRecord left = first.clone(new GenomeSpan(curRef, start1, start2, strand));

            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start2, end2, strand));
        			merged.addName(second.getNames());
	
            		write(left);            			
            		first = merged;
        				        			
	            } else if (start2 == start1 && end2 > end1) {
            		//
            		// A |-------|
            		// B |-----------|
            		//   mmmmmmmmmRRRR
	            	
            		MultinameBedRecord merged = first.clone(new GenomeSpan(curRef, start1, end1, strand));
        			merged.addName(second.getNames());
	
        			MultinameBedRecord right = second.clone(new GenomeSpan(curRef, end1, end2, strand));            			
        			
            		first = merged;
            		bufList.add(right);

	            } else if (start2 == start1 && end2 == end1) {
            		//
            		// A |-------|
            		// B |-------|
            		//

	            	first.addName(second.getNames());
        			

	            } else {
            		System.out.println("first/second unknown overlap");
            		System.out.println("first: " + first.getCoord());
            		System.out.println("second: " + second.getCoord());
            		System.exit(1);
	            }
	        }
        }    
//		System.err.println("Out of loop!");

    }
    
	private void write(MultinameBedRecord rec) throws IOException {
        List<String> outs = new ArrayList<String>();
        outs.add(rec.getCoord().ref);
        outs.add(""+rec.getCoord().start);
        outs.add(""+rec.getCoord().end);
        
        if (names != null) {
        	if (single) {
        		outs.add(rec.getFirstName());
        	} else {
        		outs.add(rec.getName(this.delim));
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
