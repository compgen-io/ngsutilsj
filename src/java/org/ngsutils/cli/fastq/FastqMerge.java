package org.ngsutils.cli.fastq;


import java.io.File;
import java.io.IOException;
import java.util.List;

import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.support.Counter;
import org.ngsutils.support.IterUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj fastq-merge")
@Command(name="fastq-merge", desc="Merges two FASTQ files (R1/R2) into one interlaced file.", cat="fastq")
public class FastqMerge extends AbstractOutputCommand {
	private FastqReader[] readers;

	public FastqMerge(){
	}

	@Unparsed(name="FILE1 FILE2")
	public void setFilenames(List<File> files) throws IOException {
		if (files.size() != 2) {
			System.err.println("You must supply two files to merge!");
			System.exit(1);
		}
		
		this.readers = new FastqReader[2];
		this.readers[0] = new FastqReader(files.get(0));
		this.readers[1] = new FastqReader(files.get(1));
	}

	public void merge() throws IOException {
		if (verbose) {
			System.err.println("Merging files:");
			System.err.println("    "+readers[0].getFilename());
			System.err.println("    "+readers[1].getFilename());
		}
		
		final Counter counter = new Counter();
		
		IterUtils.zip(readers[0], readers[1], new IterUtils.Each<FastqRead, FastqRead>() {
			public void each(FastqRead one, FastqRead two) {
				counter.incr();
				if (one.getName().equals(two.getName())) {
					try {
						one.write(out);
						two.write(out);
					} catch (IOException e) {
						System.err.println(e);
						System.exit(1);
					}
				} else {
					System.err.println("Error! Unpaired files! ");
					System.exit(1);
				}
			}
		});
		
		if (verbose) {
			System.err.println("Total reads: "+counter.getValue());
		}
	}

	@Override
	public void exec() throws Exception {
			merge();
	}
}
