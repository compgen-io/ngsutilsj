package org.ngsutils.fastq;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.ngsutils.cli.Command;
import org.ngsutils.cli.NGSExec;
import org.ngsutils.support.Counter;
import org.ngsutils.support.IterUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj fastq-merge")
@Command(name="fastq-merge", desc="Merges two FASTQ files (R1/R2) into one interlaced file.", cat="fastq")
public class FastqMerge implements NGSExec {
	private FastqReader[] readers;

	private String outputName = "-";
	private boolean compressOuput = false;
	private boolean verbose = false;

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

	@Option(description="Output filename (default: stdout)", shortName="o", defaultValue="-", longName="output")
	public void setOutputName(String outputName) throws IOException {
		this.outputName = outputName;
	}

    @Option(helpRequest=true, description="Display help", shortName="h")
    public void setHelp(boolean help) {
	}

	@Option(description="Compress output (default: false)", shortName="z", longName="compress")
	public void setCompressOuput(boolean compressOuput) {
		this.compressOuput = compressOuput;
	}

	@Option(description="Verbose output", shortName="v", defaultValue="false")
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void merge() throws IOException {
		final OutputStream out;
		if (outputName.equals("-")) {
			out = System.out;
		} else if (compressOuput) {
			out = new GZIPOutputStream(new FileOutputStream(outputName));

		} else {
			out = new BufferedOutputStream(new FileOutputStream(outputName));
		}

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
