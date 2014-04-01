package org.ngsutils.fastq;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.ngsutils.Command;
import org.ngsutils.NGSExec;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.support.Counter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutils fastq-split")
@Command(name = "fastq-split", desc = "Splits an interlaced FASTQ file by read number.")
public class FastqSplit implements NGSExec {
	private FastqReader reader;

	private boolean readOne = false;
	private boolean readTwo = false;
	private String outputName = "-";
	private boolean compressOuput = false;
	private boolean verbose = false;

	public FastqSplit() {
	}

	@Unparsed(name = "FILE")
	public void setFilename(String filename) throws IOException {
		this.reader = new FastqReader(filename);
	}

	@Option(description = "Export read 1", shortName = "R1")
	public void setReadOne(boolean value) {
		this.readOne = value;
	}

	@Option(description = "Export read 2", shortName = "R2")
	public void setReadTwo(boolean value) {
		this.readTwo = value;
	}

	@Option(description = "Output filename (default: stdout)", shortName = "o", defaultValue = "-", longName = "output")
	public void setOutputName(String outputName) throws IOException {
		this.outputName = outputName;
	}

	@Option(helpRequest = true, description = "Display help", shortName = "h")
	public void setHelp(boolean help) {
	}

	@Option(description = "Compress output (default: false)", shortName = "z", longName = "compress")
	public void setCompressOuput(boolean compressOuput) {
		this.compressOuput = compressOuput;
	}

	@Option(description = "Verbose output", shortName = "v", defaultValue = "false")
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void split() throws IOException {
		final OutputStream out;
		if (outputName.equals("-")) {
			out = System.out;
		} else if (compressOuput) {
			out = new GZIPOutputStream(new FileOutputStream(outputName));

		} else {
			out = new BufferedOutputStream(new FileOutputStream(outputName));
		}

		if (verbose) {
			System.err.println("Spliting file:" + reader.getFilename());
			if (readOne) {
				System.err.println("Exporting read 1");
			} else {
				System.err.println("Exporting read 1");
			}
		}

		Counter counter = new Counter();
		boolean isRead1 = true;
		for (FastqRead read : reader) {
			if (readOne && isRead1) {
				counter.incr();
				read.write(out);
			} else if (readTwo && !isRead1) {
				counter.incr();
				read.write(out);
			}
			isRead1 = !isRead1;
		}
		
		if (verbose) {
			System.err.println("Total reads: " + counter.getValue());
		}
	}

	@Override
	public void exec() throws Exception {
		split();
	}
}
