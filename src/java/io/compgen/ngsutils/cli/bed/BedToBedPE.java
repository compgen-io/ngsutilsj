package io.compgen.ngsutils.cli.bed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.annotation.BadReferenceException;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.support.PeakableIterator;

@Command(name="bed-tobedpe", desc="Combine two name-sorted BED files to BEDPE format", category="bed", doc=""
		+ "The two input files must be consistently sorted by name, but don't need to be sorted\n"
		+ "by ngsutilsj bed-sort. The input files will be read in batches, so long as the\n"
		+ "sorted records are near each other, the records can be easily merged. In the \n"
		+ "case of multiple records for a single read, the closest pairs will be kept (same \n"
		+ "chromosome, different strands, smallest insert distance). In cases with only\n"
		+ "discordant reads, the first combination found will be written. Output file is not\n"
		+ "guaranteed to be in the same name-sorted order as input. Memory required is \n"
		+ "based on the number of unmatched reads from both files.")

public class BedToBedPE extends AbstractOutputCommand {
    
    private String[] filenames = null;
	private int batchSize = 10000;
    protected TabWriter writer = null;
	
    @UnnamedArg(name = "FILE1 FILE2")
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames == null || filenames.length != 2) {
    		throw new CommandArgumentException("You must specify exactly two input files");
    	}
        this.filenames = filenames;
        
    }

    @Option(desc = "Number of BED regions to read/keep in a buffer (default: 10000)", charName="b")
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    @Exec
    public void exec() throws IOException, CommandArgumentException {
		int total = 0;
		writer = new TabWriter(out);

		Map<String, BedRecord[]> reads1 = new TreeMap<String, BedRecord[]>();
    	Map<String, BedRecord[]> reads2 = new TreeMap<String, BedRecord[]>();
    	
    	PeakableIterator<BedRecord> file1 = new PeakableIterator<BedRecord>(BedReader.readFile(filenames[0]));
    	PeakableIterator<BedRecord> file2 = new PeakableIterator<BedRecord>(BedReader.readFile(filenames[1]));    	
    	
    	while (file1.hasNext() || file2.hasNext() || reads1.size()>0 || reads2.size()>0) {
			int start1 = reads1.size();
    		while (file1.hasNext() && reads1.size() - start1 < batchSize) {
    			List<BedRecord> curReads = new ArrayList<BedRecord>();
    			curReads.add(file1.next());
    			
    			while (file1.hasNext() && file1.peek().getName().equals(curReads.get(0).getName())) {
    				curReads.add(file1.next());
    			}    			
    			
    			reads1.put(curReads.get(0).getName(), (BedRecord[]) curReads.toArray(new BedRecord[curReads.size()]));
    		}
    		
			int start2 = reads2.size();
    		while (file2.hasNext() && reads2.size() - start2 < batchSize) {
    			List<BedRecord> curReads = new ArrayList<BedRecord>();
    			curReads.add(file2.next());
    			
    			while (file2.hasNext() && file2.peek().getName().equals(curReads.get(0).getName())) {
    				curReads.add(file2.next());
    			}    			

    			reads2.put(curReads.get(0).getName(), (BedRecord[]) curReads.toArray(new BedRecord[curReads.size()]));
    		}
    		
    		if (verbose) {
    			System.err.println("Populated buffer sizes  1: "+reads1.size() + ", 2: "+reads2.size());
    		}
    		
    		int written = mergeReads(reads1, reads2);
    		total += written;

    		if (verbose) {
    			System.err.println("Post-write buffer sizes 1: "+reads1.size() + ", 2: "+reads2.size());
    			System.err.println("Total written: " + total);
    			System.err.println("----");
    		}

    		if (written == 0 && !file1.hasNext() && !file2.hasNext()) {
    			// if we can't add reads and we haven't written any matches, break
    			break;
    		}
    	}

		writer.close();    	

		System.err.println("Total written: " + total);
		System.err.println("Unmatched reads 1:" + reads1.size() +", 2: " + reads2.size());
    }
    
    protected int mergeReads(Map<String, BedRecord[]> read1, Map<String, BedRecord[]> read2) throws IOException {    	
    	Set<String> removeKeys = new HashSet<String>();
    	
		for (String name: read1.keySet()) {
			if (read2.containsKey(name)) {
				removeKeys.add(name);
				outputBEDPE(read1.get(name), read2.get(name));
			}
    	}
    	
    	for (String name: removeKeys) {
    		read1.remove(name);
    		read2.remove(name);
    	}    	
    	
    	return removeKeys.size();
    }

	private void outputBEDPE(BedRecord[] bed1, BedRecord[] bed2) throws IOException {
		int bestDist = -1;
		int bestIdx1 = 0;
		int bestIdx2 = 0;
		
		for (int i=0; i<bed1.length; i++) {
			BedRecord r1 = bed1[i];
			for (int j=0; j<bed2.length; j++) {
				BedRecord r2 = bed2[j];
				// same chrom ?
				if (r1.getCoord().ref.equals(r2.getCoord().ref)) {
					// opposite strands (or unstranded)?
					if (r1.getCoord().strand == Strand.NONE || r2.getCoord().strand == Strand.NONE || r1.getCoord().strand != r2.getCoord().strand) {
						try {
							int dist = Math.abs(r1.getCoord().distanceTo(r2.getCoord()));
							if (bestDist == -1 || dist < bestDist) {
								bestIdx1 = i;
								bestIdx2 = j;
								bestDist = dist;
							}
						} catch (BadReferenceException e) {
						}
					}
				}
			}
		}
		
		double score = Math.max(bed1[bestIdx1].getScore(), bed2[bestIdx2].getScore());
		String scoreStr = "" + score;
		if (scoreStr.endsWith(".0")) {
			scoreStr = scoreStr.substring(0, scoreStr.length() - 2);
		}
		
		GenomeSpan read1 = bed1[bestIdx1].getCoord(); 
		GenomeSpan read2 = bed2[bestIdx2].getCoord(); 
		
		writer.write(read1.ref);
		writer.write(read1.start);
		writer.write(read1.end);
		writer.write(read2.ref);
		writer.write(read2.start);
		writer.write(read2.end);
		writer.write(bed1[bestIdx1].getName());
		writer.write(scoreStr);
		writer.write(read1.strand.toString());
		writer.write(read2.strand.toString());
		writer.eol();
	}
}
