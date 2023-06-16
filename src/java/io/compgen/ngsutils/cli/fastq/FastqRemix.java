package io.compgen.ngsutils.cli.fastq;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name="fastq-remix", desc="Remix one or more different FASTQ files in different sampling ratios", category="fastq", experimental=true, 
doc="\nYou can use any number of inputs. If you use one input file, this program \n"
  + "will downsample the number of reads (--total). If you use two or more files, a mix of \n"
  + "reads from each file will be used. You can set the specific ratio of reads \n"
  + "from each file using --ratio. If you want to have a consistent mixture of reads (a \n"
  + "consistent randomization), then you can specify a fixed --seed value for the random \n"
  + "number generator. If an interleaved FASTQ file is given, then all reads will be written \n"
  + "to output.\n"
  + "\n"
  + "Note: this is not an optimal process. Each FASTQ input has to be scanned twice. Once to \n"
  + "determine the number of reads in the file, and once to write the selected reads."
)
public class FastqRemix extends AbstractOutputCommand {
	private List<String> filenames = null;
	private List<Double> ratios = null;
	private List<String> ratiosOrig = null;
	private int totalReads = -1;
	private Random random = null;
		
	public FastqRemix(){
	}

   @Option(desc="Set the seed for the random number generator (use same value for paired-end files), integer", name="seed")
    public void setSeed(int seed) {
        random = new Random(seed);
    }

   @Option(desc="Comma delimited list of the file ratios (ex: 0.75,0.25 or 3,1)", name="ratio")
   public void setRatios(String vals) throws CommandArgumentException {
	   this.ratios = new ArrayList<Double>();
	   this.ratiosOrig = new ArrayList<String>();
	   
	   String[] spl = vals.split(",");
	   double total = 0.0;
	   
	   double[] dvals = new double[spl.length];
	   for (int i=0; i<spl.length; i++) {
		   try {
			   dvals[i] = Double.parseDouble(spl[i]);
			   total += dvals[i];
			   ratiosOrig.add(spl[i]);
		   } catch (NumberFormatException e) {
			   throw new CommandArgumentException(e);
		   }
	   }
	   
	   for (double dval: dvals) {
		   this.ratios.add(dval / total);
	   }
   }
   
   @Option(desc="Total number of reads to include in the output (int)", name="total")
   public void setTotalReads(int totalReads) {
       this.totalReads = totalReads;
   }
   
	@UnnamedArg(name="FASTQ1 {FASTQ2}")
	public void setFilenames(String[] filenames) throws IOException {
		this.filenames = new ArrayList<String>();
		for (String f: filenames) {
			this.filenames.add(f);			
		}
	}

   @Exec
   public void exec() throws IOException, CommandArgumentException {
        if (filenames == null || filenames.size() == 0) {
            throw new CommandArgumentException("You must supply at least one FASTQ file.");
        }
		for (int i=0; i<filenames.size(); i++) {
			File f = new File(filenames.get(i));
			if (!f.exists()) {
				throw new CommandArgumentException("Missing file: " + filenames.get(i));
			}
		}

        if (ratios == null) {
     	   	this.ratios = new ArrayList<Double>();
     	   	this.ratiosOrig = new ArrayList<String>();

        	if (filenames.size() > 1) {
        		System.err.println("Using even mixture ratio");
	        	
	     	   	for (int i=0; i<filenames.size(); i++) {
	     	   		this.ratios.add(1.0 / filenames.size());
	    		    this.ratiosOrig.add("1");
	     	   	}
        	} else {
     	   		this.ratios.add(1.0);
    		    this.ratiosOrig.add("1");
        	}
        }
        
        if (random == null) {
        	// if you don't specify a seed, we'll choose one, but to make it easier to display to the user,
        	// we will restrict the seeds to a positive value. 
        	//
        	// Note: this only has an effect when --seed isn't set. 
        	//
        	
        	int seed = Math.abs(new Random().nextInt());
        	System.err.println("Using random seed: " + seed);
        	random = new Random(seed);
        }
        
		if (verbose) {
			System.err.println("Total number of reads: " + totalReads);
			if (filenames.size() == 1) {
				System.err.println("Downsampling file: " + filenames.get(0));
			} else {
				System.err.println("Mixing files: " + filenames.get(0) + " ("+ratiosOrig.get(0)+")");
				for (int i=1; i<filenames.size(); i++) {
					System.err.println("              " + filenames.get(i) + " ("+ratiosOrig.get(i)+")");
				}
			}
		}
		
		//
		// The first pass is to get the total number of reads for each file
		// This also does a sanity check to make sure that either all or none
		// of the input files are interleaved.
		//
		
		int[] fileReadCount = new int[filenames.size()];
		int[] targetReadCount = new int[filenames.size()];
		boolean anyInterleaved = false;
		
		for (int i=0; i<filenames.size(); i++) {
			fileReadCount[i] = 0;
			targetReadCount[i] = (int) Math.ceil(totalReads * ratios.get(i));

			System.err.println("File: " + filenames.get(i));
			System.err.println("Target reads: " + targetReadCount[i]);
			
    		FileInputStream fis1 = new FileInputStream(filenames.get(i));
    		FileChannel channel1 = fis1.getChannel();
			FastqReader reader = Fastq.open(fis1, null, channel1, filenames.get(i));
			
			String lastRead = null;
			boolean interleaved = false;
			
	        for (FastqRead read: reader) {
				// this check supports interleaved FASTQ files
				if (lastRead == null || !lastRead.equals(read.getName())) {
					fileReadCount[i]++;
					lastRead = read.getName();
				} else if (lastRead != null && lastRead.equals(read.getName())) {
					interleaved = true;
				}
			}
			reader.close();

			if (interleaved) {
				System.err.println("File reads: " + fileReadCount[i] + " (interleaved)");
				if (i > 0 && !anyInterleaved) {
					throw new CommandArgumentException("You cannot mix interleaved and split FASTQ files.");
				}
				anyInterleaved = true;
			} else {
				System.err.println("File reads: " + fileReadCount[i]);
				if (i > 0 && anyInterleaved) {
					throw new CommandArgumentException("You cannot mix interleaved and split FASTQ files.");
				}
			}
		}

		//
		// The second pass is to write the correct number of reads for each input file
		//

		for (int i=0; i<filenames.size(); i++) {
			System.err.println("Shuffling reads...");
			// find all reads we will be keeping. First, build an array of the index of all reads (1..file_read_count)
            int[] idx = new int[fileReadCount[i]];
            for (int j=0; j<fileReadCount[i]; j++) {
            	idx[j] = j;
            }

//            System.err.println("Read idx: " + StringUtils.join(", ", idx));
            
            // Shuffle array
            for (int j=idx.length; j>1; j--) {
            	int swapIdx = random.nextInt(j);
            	int tmp = idx[j-1];
            	idx[j-1] = idx[swapIdx];
            	idx[swapIdx] = tmp;
            }

//            System.err.println("Shuffled: " + StringUtils.join(", ", idx));

            // Take the first ${targetReadCount[i]} reads and sort that subset
            int[] valid = new int[targetReadCount[i]];
            for (int j=0; j<targetReadCount[i]; j++) {
            	valid[j] = idx[j];
            }

//            System.err.println("Valid   : " + StringUtils.join(", ", valid));

            
            Arrays.sort(valid);
//            System.err.println("Sorted  : " + StringUtils.join(", ", valid));
			System.err.println("Writing reads...");

            int validIdx = 0;

            int written = 0;
            
            // Now we can iterate over the file again, and if the read number (pos/index) matches a valid index, write the read to stdout.
    		FileInputStream fis1 = new FileInputStream(filenames.get(i));
    		FileChannel channel1 = fis1.getChannel();
			FastqReader reader = Fastq.open(fis1, null, channel1, filenames.get(i));
			
			String lastRead = null;
			int curReadNum = 0;
			boolean currentValid = false;
			
			// This is a multi-step process to support interleaved FASTQ files
			// 
			// First, if the read's name doesn't match the prior name, we increment the read number (and reset the valid flag)
						
	        for (FastqRead read: reader) {
				if (lastRead != null && !lastRead.equals(read.getName())) {
					curReadNum++;
					if (currentValid) {
						validIdx++;
					}
					currentValid = false;
					if (validIdx >= valid.length) {
						break;
					}
				}

				if (curReadNum == valid[validIdx]) {
					currentValid = true;
				}

				if (currentValid) {
					if (lastRead == null || !lastRead.equals(read.getName())) {
						written++;
					}
					read.write(out);
				}

				lastRead = read.getName();
			}
	        System.err.println("Reads written: " + written);
			reader.close();            
		}

	}

}
