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

@Command(name="bed-combine", desc="Given two or more (sorted) BED files, combine the BED annotations into one output BED file. This will produce non-overlapping regions (unlike bed-reduce).", category="bed")
public class BedCombine extends AbstractOutputCommand {
    
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
        int counter = 0;

        // key = chrom
    	// list1 = for each input bed file
    	//  list2 = the records for that specific bedfile
        Map<String, List<List<BedRecord>>> records = new HashMap<String, List<List<BedRecord>>>();
        List<String> refs = new ArrayList<String>();

        for (int i=0; i<bedFilenames.size(); i++) {
        	Iterator<BedRecord> it = BedReader.readFile(bedFilenames.get(i), ignoreStrand); //ignoreStrand is redundant here...
        	while (it.hasNext()) {
        		BedRecord rec = it.next();
        		if (rec.getCoord().strand.matches(strand)) {
        			// add the records
        			if (!records.containsKey(rec.getCoord().ref)) {
        				refs.add(rec.getCoord().ref);
        				records.put(rec.getCoord().ref, new ArrayList<List<BedRecord>>());
        			}
        			while (records.get(rec.getCoord().ref).size() <= i) {
        				records.get(rec.getCoord().ref).add(new ArrayList<BedRecord>());
        			}
        			records.get(rec.getCoord().ref).get(i).add(rec);
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
				records.get(ref).add(new ArrayList<BedRecord>());
				records.get(ref).get(records.get(ref).size()-1).add(refBed);
        	}
        	if (defval != null) {
        		names.add(defval);
        	} else {
        		names.add(null);
        	}
        }

        for (String curRef: refs) {
//        	System.out.println("["+curRef+"]");
        	List<List<BedRecord>> curRecords = records.get(curRef);
        	while (true) {
	         	int firstIdx = -1;
	        	@SuppressWarnings("unused")
				int secondIdx = -1;
	        	BedRecord first = null;
	        	BedRecord second = null;
	        	
	            for (int i=0; i<curRecords.size(); i++)  {
	            	if (curRecords.get(i).size() > 0) {
//	            		System.out.println("["+i+"] " + curRecords.get(i).get(0));
	        			BedRecord rec = curRecords.get(i).get(0); // the lists are sorted...
	        			if (first == null || rec.compareTo(first) < 0) {
	        				if (first != null) {
	        					second = first;
	        					secondIdx = firstIdx;
	        				}
	        				first = rec;
	        				firstIdx = i;
	        			} else if (second == null || rec.compareTo(second) < 0) {
	        				second = rec;
	        				secondIdx = i;
	            		}
	            	}
	            }
	
	            if (first == null && second == null) {
	            	break;
	            }
	            
//	            System.out.println("1 ["+firstIdx+"]" + first + " " + first.getCoord());
//	            System.out.println("2 ["+secondIdx+"]" + second + " " + second.getCoord());
	            
	            if (first.getCoord().overlaps(second.getCoord())) {
	            	// overlap, so find start/end and update
	            	int start = first.getCoord().start;
	            	int end = second.getCoord().start;
	            	
	            	if (start == end) {
	            		if (first.getCoord().end < second.getCoord().end) {
	            			end = first.getCoord().end;
	            		} else {
	            			end = second.getCoord().end;
	            		}
	            	}
	            	
	            	GenomeSpan newspan = new GenomeSpan(curRef, start, end, strand);
//	            	System.out.println(newspan);
	
	            	// find all overlapping records
	            	List<String> newnames = new ArrayList<String>();
	            	for (int i=0; i< curRecords.size(); i++) {
	            		if (curRecords.get(i).get(0).getCoord().overlaps(newspan)) {
	            			BedRecord match = curRecords.get(i).get(0);
	            			if (names.get(i) == null) {
	            				newnames.add(match.getName());
	            			} else {
	            				newnames.add(names.get(i));
	            			}
	            			
	            			if (match.getCoord().end == end) {
	            				curRecords.get(i).remove(0);
	            			} else {
		            			// update these spans
		            			GenomeSpan newcoord = new GenomeSpan(curRef, end, match.getCoord().end, match.getCoord().strand);
		            			BedRecord newRec = match.clone(newcoord);
		            			curRecords.get(i).set(0,  newRec);
	            			}
	            		}
	            	}
	            
	        		BedRecord newrec;
	        		if (single) {
	        			newrec = new BedRecord(newspan, newnames.get(0));
	        		} else {
	        			newrec = new BedRecord(newspan, StringUtils.join("|", newnames));
	        		}
	            	
	        		write(newrec);
	            	
	            } else {
	            	// no overlap, write the first and remove it from tree
	            	write(first, names.get(firstIdx));
	            	curRecords.get(firstIdx).remove(0);
	            }
	            
	            if (counter++ > 100) {            
	            	return;
	            }
        	}
        }   
    }

	private void write(BedRecord rec) throws IOException {
		write(rec, null);
	}
	private void write(BedRecord rec, String name) throws IOException {
		if (name != null) {
			rec.clone(name).write(out);
		} else {
			rec.write(out);
		}
	}
}
