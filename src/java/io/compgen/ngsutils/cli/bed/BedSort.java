package io.compgen.ngsutils.cli.bed;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

@Command(name="bed-sort", desc="Sort BED file (by coordinate or name)", category="bed")
public class BedSort extends AbstractOutputCommand {
    
    private String filename = null;
    private boolean byCoord = true;
    private int bufferSize = 100000;
    
    @Option(charName="n", name="name", desc="Sort by name")
    public void setName(boolean val) {
        this.byCoord = !val;
    }

    @Option(charName="c", name="coord", desc="Sort by coordinate (default)")
    public void setCoord(boolean val) {
        this.byCoord = val;
    }

    @Option(charName="b", desc="Number of records to write to temp files (default: 100000)")
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        List<File> tmpFiles = new ArrayList<File>();        
        List<BedRecord> buf1 = new ArrayList<BedRecord>();
        
        Comparator<BedRecord> compFunc = new Comparator<BedRecord>() {
			@Override
			public int compare(BedRecord o1, BedRecord o2) {
				if (byCoord) {
					return o1.getCoord().compareTo(o2.getCoord());
				} 
				return o1.getName().compareTo(o2.getName());
			}};
        
		// Read in the BED file, sorting the records and writing them to temp (gzip'd) output files
			
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(filename))) {
        	if (!byCoord && record.getName() == null) {
        		throw new IOException("Missing name from BED Record: " + record.getCoord());
        	}
    		buf1.add(record);
    		
    		if (buf1.size()>=bufferSize) {
    			Collections.sort(buf1, compFunc);
    			
    			File tmp = File.createTempFile(".ngsutilsj_bed_sort", null);
    			tmp.deleteOnExit();
    			GZIPOutputStream os = new GZIPOutputStream( new FileOutputStream(tmp));
    			for (BedRecord rec: buf1) {
    				rec.write(os);
    			}
    			os.flush();
    			os.close();
    			tmpFiles.add(tmp);
    			buf1.clear();
    		}
        }
        
        if (buf1.size()>0) {
			Collections.sort(buf1, compFunc);
			
			File tmp = File.createTempFile(".ngsutilsj_bed_sort", null);
			tmp.deleteOnExit();
			GZIPOutputStream os = new GZIPOutputStream( new FileOutputStream(tmp));
			for (BedRecord rec: buf1) {
				rec.write(os);
			}
			os.flush();
			os.close();
			tmpFiles.add(tmp);
			buf1.clear();
        }
	    
		// Read in the temp files and sort those records, write to final output
		LinkedList<Pair<Integer, BedRecord>> merge = new LinkedList<Pair<Integer, BedRecord>>();
	    List<Iterator<BedRecord>> readers = new ArrayList<Iterator<BedRecord>>();
	    
	    for (File f: tmpFiles) {
	    	readers.add(BedReader.readFile(f));
	    }
	    
	    for (int i=0; i<readers.size(); i++) {
	    	if (readers.get(i).hasNext()) {
	    		merge.add(new Pair<Integer, BedRecord>(i, readers.get(i).next()));
	    	}
	    }
	    
	    while (merge.size() > 0) {
	    	Collections.sort(merge, new Comparator<Pair<Integer, BedRecord>>(){

				@Override
				public int compare(Pair<Integer, BedRecord> o1, Pair<Integer, BedRecord> o2) {
					if (byCoord) {
						return o1.two.getCoord().compareTo(o2.two.getCoord());
					}
					return o1.two.getName().compareTo(o2.two.getName());
				}});
	    	
	    	Pair<Integer, BedRecord> record = merge.remove();
	    	record.two.write(out);
	    	if (readers.get(record.one).hasNext()) {
	    		merge.add(new Pair<Integer, BedRecord>(record.one, readers.get(record.one).next()));
	    	}
	    }

	    // Remove temp files
	    for (File f: tmpFiles) {
	    	f.delete();
	    }	    
	}    
}
