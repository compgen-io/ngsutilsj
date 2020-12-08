package io.compgen.ngsutils.cli.fastq;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name = "fastq-overlap", desc = "For paired FASTQ files, attempt to find overlapping reads", category="fastq")
public class FastqOverlap extends AbstractCommand {
    private String[] filenames;
    private String overlapFilename;
    private String splitFilename = null;
    private String splitFilename1 = null;
    private String splitFilename2 = null;
    private int minOverlap = 10;
    private boolean gzip = false;
    private boolean interleaved = false;

	public FastqOverlap() {
	}

    @UnnamedArg(name = "R1.fastq [R2.fastq]", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
        this.filenames = filenames;

        for (String filename: filenames) {
	        if (!filename.equals("-")) {
	            if (!new File(filename).exists()) {
	                throw new CommandArgumentException("Missing file: "+filename);
	            }
	        }
        }
    }

    @Option(name="interleaved", desc="Input FASTQ file is interleaved")
    public void setInterleaved(boolean interleaved) throws IOException {
        this.interleaved = interleaved;
    }
    
    @Option(name="min-overlap", desc="Minimum amount of bases required to merge reads", defaultValue="10")
    public void setMinOverlap(int minOverlap) throws IOException {
        this.minOverlap = minOverlap;
    }
    
    @Option(name="overlap", charName="o", desc="Write all overlapping reads to this FASTQ file (filenames ending in .gz will be compressed, - for stdout)", required=true, helpValue="fname")
    public void setOverlap(String overlap) throws IOException {
        this.overlapFilename = overlap;
    }
    
    @Option(name="split", desc="Write all split reads to this (interleaved) FASTQ file (filenames ending in .gz will be compressed)", helpValue="fname")
    public void setSplit(String split) throws IOException {
        this.splitFilename = split;
    }
    
    @Option(name="split1", desc="Write all split R1 reads to this FASTQ file (filenames ending in .gz will be compressed)", helpValue="fname")
    public void setSplit1(String split) throws IOException {
        this.splitFilename1 = split;
    }
    
    @Option(name="split2", desc="Write all split R2 reads to this FASTQ file (filenames ending in .gz will be compressed)", helpValue="fname")
    public void setSplit2(String split) throws IOException {
        this.splitFilename2 = split;
    }
    
    @Option(name="gz", desc="Output files should be gzip compressed (regardless of suffix)")
    public void setGZip(boolean value) {
        this.gzip = value;
    }

	@Exec
    public void exec() throws IOException, CommandArgumentException, NoSuchAlgorithmException {

        if (overlapFilename == null) {
        	throw new CommandArgumentException("Missing --overlap argument");
        }
        
		if (!interleaved) {
	        if (filenames.length != 2) {
	        	throw new CommandArgumentException("You must specify both R1 and R2 files");
	        }
	        
	        if (filenames[0].equals("-") || filenames[1].equals("-") ) {
	        	throw new CommandArgumentException("R1 and R2 files can not be read from stdin (stdin input is only allowed for interleaved FASTQ)");
	        }
		} else {
	        if (filenames.length != 1) {
	        	throw new CommandArgumentException("You must specify one (and only one) interleaved FASTQ file");
	        }
	        
	        if (splitFilename != null && (splitFilename1 != null || splitFilename2 != null)) {
	        	throw new CommandArgumentException("You cannot specify both --split and --split1/--split2");
	        }
		}
        
        OutputStream outOverlap = null;
        OutputStream outSplit = null;
        OutputStream outSplit1 = null;
        OutputStream outSplit2 = null;

        if (overlapFilename.equals("-")) {
        	outOverlap = System.out;
        } else if (gzip || overlapFilename.endsWith(".gz")) {
        	outOverlap = new GZIPOutputStream(new FileOutputStream(overlapFilename));
        } else {
        	outOverlap = new FileOutputStream(overlapFilename);
        }

        if (splitFilename != null) {
        	if (gzip || splitFilename.endsWith(".gz")) {
	        	outSplit = new GZIPOutputStream(new FileOutputStream(splitFilename));
	        } else {
	        	outSplit = new FileOutputStream(splitFilename);
	        }
        }

        if (splitFilename1 != null) { 
        	if (gzip || splitFilename1.endsWith(".gz")) {
	        	outSplit1 = new GZIPOutputStream(new FileOutputStream(splitFilename1));
	        } else {
	        	outSplit1 = new FileOutputStream(splitFilename1);
	        }
        }

        if (splitFilename2 != null) {
        	if (gzip || splitFilename2.endsWith(".gz")) {
	        	outSplit2 = new GZIPOutputStream(new FileOutputStream(splitFilename2));
	        } else {
	        	outSplit2 = new FileOutputStream(splitFilename2);
	        }
        }

        final FastqReader reader1;
        final FastqReader reader2;

        if (filenames[0].equals("-")) {
	        reader1 = Fastq.open(filenames[0]);
        } else {
	        FileInputStream fis1 = new FileInputStream(filenames[0]);
	        reader1 = Fastq.open(fis1, null, fis1.getChannel(), filenames[0]);
        }

        if (interleaved) {
        	reader2 = null; 
        } else {
            FileInputStream fis2 = new FileInputStream(filenames[1]);
        	reader2 = Fastq.open(fis2, null, null, filenames[1]);
        }

        Iterator<FastqRead> it1 = reader1.iterator();
        Iterator<FastqRead> it2 = null;
        
        if (interleaved) {
        	it2 = it1;
        } else {
        	it2 = reader2.iterator();
        }
        
        while (it1.hasNext() && it2.hasNext()) {
            FastqRead one = it1.next();
            FastqRead two = it2.next();

            if (!one.getName().equals(two.getName())) {
            	throw new IOException("Unpaired FASTQ file(s) found!");
            }
            
            FastqRead overlapRead = findOverlapRead(one, two, this.minOverlap);
            
            if (overlapRead != null) {
            	// we found an overlapping read... write it out
            	overlapRead.write(outOverlap); 
            } else {
            	// split read. 
            	// write to interleaved output or to split R1/R2 outputs.            	
            	if (outSplit != null) {
            		one.write(outSplit);
            		two.write(outSplit);
            	} else {
	            	if (outSplit1 != null) {
	            		one.write(outSplit1);
	            	}
	            	if (outSplit2 != null) {
	            		two.write(outSplit2);
	            	}
            	}
            }
            
        }
        
        
        reader1.close();
        if (reader2 != null) {
        	reader2.close();
        }
        
        if (outOverlap != System.out) {
        	outOverlap.close();
        }
        
        if (outSplit != null) {
        	outSplit.close();
        }
        
        if (outSplit1 != null) {
        	outSplit1.close();
        }
        
        if (outSplit2 != null) {
        	outSplit2.close();
        }
	}

	static public FastqRead findOverlapRead(FastqRead one, FastqRead two, int minOverlap) {
		// this is a slow algorithm that walks each read across looking for the best overlap
		// if there is a match, return a new FastqRead object that represents the overlapping sequence and qual values
		
		// R1-aaaaaaaaaaaccccc
		//                 ccccc-R2           
		
		
		String oneS = one.getSeq();
		String twoC = SeqUtils.revcomp(two.getSeq());

//		System.err.println("Two  : " + two.getSeq());
//		System.err.println("Two-c: " + twoC);
		
		
		int bestLength = -1;
		
		for (int i = minOverlap; i <= one.getSeq().length() && i <= two.getSeq().length(); i++) {
//			System.err.println("=======================================");
			
//			System.err.println(oneS);
//			for (int k=0; k < oneS.length() - i; k++ ) {
//				System.err.print(" ");
//			}
//			System.err.println(twoC);
			
			boolean match = true;
			for (int j=0; j < i; j++) {
//				System.err.print(""+oneS.charAt(one.getSeq().length()-i+j) + "==" + twoC.charAt(j) + "? ") ;
				if (oneS.charAt(one.getSeq().length()-i+j) != twoC.charAt(j)) {
//					System.err.println("no");
					match = false;
					break;
//				} else {
//					System.err.println("yes");
				}
			}
			
			if (match) {
//				System.err.println("match: " + i);
				bestLength = i;
			}
			
		}

//		System.err.println("best-match: " + bestLength);
		if (bestLength > 0) {
//			System.err.println(oneS);
//			for (int k=0; k < oneS.length() - bestLength; k++ ) {
//				System.err.print(" ");
//			}
//			System.err.println(twoC);

			
			String seq = oneS + twoC.substring(bestLength);
			String qual = one.getQual() + two.getQual().substring(bestLength);

//			System.err.println(seq);
//			System.err.println(qual);

			return new FastqRead(one.getName(), seq, qual);
		}
		
		return null;
	}

}
