package io.compgen.ngsutils.cli.fastq;


import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.Counter;
import io.compgen.common.IterUtils;
import io.compgen.common.IterUtils.EachPair;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;

@Command(name="fastq-merge", desc="Merges two FASTQ files (R1/R2) into one interleaved file.", category="fastq")
public class FastqMerge extends AbstractOutputCommand {
	private String[] filenames=null;

	private boolean ignoreReadNum = false;
	
	public FastqMerge(){
	}

   @Option(desc="Ignore Illumina read numbers in read names (/1, /2)", name="ignore-readnum")
    public void setIgnoreReadNum(boolean value) {
        this.ignoreReadNum = value;
    }

	@UnnamedArg(name="FILE1 FILE2")
	public void setFilenames(List<String> files) throws IOException {
		if (files.size() != 2) {
			System.err.println("You must supply two files to merge!");
			System.exit(1);
		}
		
		filenames = new String[2];
		filenames[0] = files.get(0);
		filenames[1] = files.get(1);
		
//		this.readers = new FastqReader[2];
//		this.readers[0] = new FastqReader(files.get(0));
//		this.readers[1] = new FastqReader(files.get(1));
	}

   @Exec
   public void exec() throws IOException, CommandArgumentException {
        if (filenames == null) {
            throw new CommandArgumentException("You must supply two FASTQ files to merge.");
        }
       
		if (verbose) {
			System.err.println("Merging files:");
			System.err.println("    "+filenames[0]);
			System.err.println("    "+filenames[1]);
		}
		
		FastqReader[] readers = new FastqReader[2];
		File file1 = new File(filenames[0]);
		FileInputStream fis1 = new FileInputStream(file1);
		FileChannel channel1 = fis1.getChannel();

		File file2 = new File(filenames[1]);
        FileInputStream fis2 = new FileInputStream(file2);

//        readers[0] = Fastq.open(filenames[0]);
//        readers[1] = Fastq.open(filenames[1]);
        readers[0] = Fastq.open(fis1, null, channel1, filenames[0]);
        readers[1] = Fastq.open(fis2, null, null, filenames[1]);
		
		final Counter counter = new Counter();
		
		IterUtils.zip(readers[0], readers[1], new EachPair<FastqRead, FastqRead>() {
			public void each(FastqRead one, FastqRead two) {
				counter.incr();
				String n1 = one.getName(); 
				String n2 = two.getName();
				
				if (ignoreReadNum) {
                    if (n1.endsWith("/1")) {
                        n1 = n1.substring(0, n1.length()-2);
                        one.setName(n1);
                        if (one.getComment() != null) {
                            one.setComment("/1 " + one.getComment());
                        } else {
                            one.setComment("/1");
                        }
                    }
                    if (n2.endsWith("/2")) {
                        n2 = n2.substring(0, n2.length()-2);
                        two.setName(n2);
                        if (two.getComment() != null) {
                            two.setComment("/2 " + two.getComment());
                        } else {
                            two.setComment("/2");
                        }
                    }
				}
                if (n1.equals(n2)) {
					try {
						one.write(out);
						two.write(out);
					} catch (IOException e) {
						System.err.println(e);
						System.exit(1);
					}
				} else {
					System.err.println("Error! Unpaired files! (Read: "+counter.getValue()+", Expected: "+one.getName()+", Got: "+two.getName()+")");
					System.exit(1);
				}
			}
		});
		close();
		if (verbose) {
			System.err.println("Total reads: "+counter.getValue());
		}
        readers[0].close();
        readers[1].close();
	}

}
