package io.compgen.ngsutils.cli.fastq;

import java.io.IOException;
import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name = "fastq-check", desc = "Verify a FASTQ single, paired, or interleaved file(s)", category="fastq")
public class FastqCheck extends AbstractCommand {
	private String[] filenames;
	private boolean colorspace = false;

	public FastqCheck() {
	}

	@UnnamedArg(name = "FILE {FILE2}")
	public void setFilename(String[] filenames) throws IOException {
	    this.filenames = filenames;
	}
	
   @Option(name="colorspace", desc="Reads are in color-space (default: base-space)")
    public void setColorspace(boolean value) {
        this.colorspace = value;
    }

	@Exec
    public void exec() throws IOException {
	    long count;
	    if (filenames.length == 1) {
	        count = execSingleFile(filenames[0]);
	    } else {
	        count = execPairedFiles(filenames[0], filenames[1]);
	    }
	    
	    if (count == -1) {
	        System.out.println("ERROR");
	        System.exit(1);
	    }
	    
	    System.out.println("OK " + count + " reads found.");
	}

	protected boolean checkPaired(FastqRead read1, FastqRead read2) {
	    if (read2 != null) {
	        if (!read1.getName().equals(read2.getName())) {
	            return false;
	        }
	    }
	    return true;
	}

	protected boolean checkSeqQualLength(FastqRead read) {
        if (colorspace) {
            if ((read.getSeq().length()+1) != read.getQual().length()) {
                // prefixed colorspace
                return false;
            }
        } else if (read.getSeq().length() != read.getQual().length()) {
            return false;
        }
        return true;
	}
	
	protected long execPairedFiles(String filename1, String filename2) throws IOException {
	    System.err.println("Reading files: "+filename1+", "+filename2);
        final FastqReader reader1 = Fastq.open(filename1);
        final FastqReader reader2 = Fastq.open(filename2, true);

        Iterator<FastqRead> it1 = reader1.iterator();
        Iterator<FastqRead> it2 = reader2.iterator();

        long count = 0;
        
        while (it1.hasNext() && it2.hasNext()) {
            FastqRead one = it1.next();
            FastqRead two = it2.next();
            if (!checkPaired(one, two)) {
                System.err.println("Unpaired read found! " + one.getName());
                reader1.close();
                reader2.close();
                return -1;
            }
            if (!checkSeqQualLength(one)) {
                System.err.println("Read seq/qual length mismatch! " + one.getName());
                reader1.close();
                reader2.close();
                return -1;
            }
            if (!checkSeqQualLength(two)) {
                System.err.println("Read seq/qual length mismatch! " + two.getName());
                reader1.close();
                reader2.close();
                return -1;
            }
            count++;
        }
        
        return count;
	}

	
	protected long execSingleFile(String filename) throws IOException {
        System.err.println("Reading file: "+filename);
		FastqReader reader = Fastq.open(filename);
        long count = 0;

        boolean paired = false;
        FastqRead lastRead = null;
		
		for (FastqRead read : reader) {
		    // check paired-ness
		    if (lastRead==null) {
		        // first read
                lastRead = read;
                count++;
            } else {
                // potential second read
		        if (read.getName().equals(lastRead.getName())) {
		            // don't count a paired read
		            paired = true;
		        }
		        
		        if (paired) {
		            if (!checkPaired(lastRead, read)) {
                        System.err.println("Unpaired read found! " + read.getName());
                        reader.close();
                        return -1;
		            }
		            lastRead = null;
		        } else {
		            // unpaired, count it.
	                count++;
                    lastRead = null;
		        }
		    }
		    if (!checkSeqQualLength(read)) {
                System.err.println("Read seq/qual length mismatch! " + read.getName());
                reader.close();
                return -1;
		    }
		}
		
		if (paired && lastRead != null) {
		    System.err.println("Trailing read-pair missing");
	        reader.close();
		    return -1;
		}
		
		reader.close();
		return count;
	}
}
