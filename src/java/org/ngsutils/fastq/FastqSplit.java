package org.ngsutils.fastq;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.Command;
import org.ngsutils.cli.NGSExec;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-split")
@Command(name = "fastq-split", desc = "Splits an FASTQ file into smaller files", cat="fastq")
public class FastqSplit implements NGSExec {
	private FastqReader reader;

	private String outputTemplate = null;
	private boolean compressOuput = false;
	private int num = 2;
	private boolean verbose = false;

	public FastqSplit() {
	}

	@Unparsed(name = "FILE")
	public void setFilename(String filename) throws IOException {
		this.reader = new FastqReader(filename);
	}

	@Option(description = "Number of subfiles to split into", shortName = "n", longName="num")
	public void setNum(int num) {
		this.num = num;
	}

	@Option(description = "Output filename template", shortName = "o", defaultToNull=true, longName = "output")
	public void setOutputTemplate(String outputTemplate) throws IOException {
		this.outputTemplate = outputTemplate;
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
		final OutputStream[] outs = new OutputStream[num];
		for (int i=0; i<num; i++) {
			if (compressOuput) {
				outs[i] = new GZIPOutputStream(new FileOutputStream(outputTemplate+"."+i+".fastq.gz"));

			} else {
				outs[i] = new BufferedOutputStream(new FileOutputStream(outputTemplate+"."+i+".fastq"));
			}
		}

		if (verbose) {
			System.err.println("Spliting file:" + reader.getFilename());
			System.err.println("Output template:" + outputTemplate);
		}

		int i = -1;
		String lastName = null;
		for (FastqRead read : reader) {
			if (read.getName().equals(lastName)) {
				read.write(outs[i]);
			} else {
				i++;
				if (i >= num) {
					i = 0;
				}
				read.write(outs[i]);
				lastName = read.getName();
			}
		}	
	}

	@Override
	public void exec() throws Exception {
		if (outputTemplate == null) {
			if (reader.getFilename().equals("-")) {
				throw new NGSUtilsException("You must specify an output template if reading from stdin");
			}
			if (reader.getFilename().contains(".fastq")) {
				outputTemplate = reader.getFilename().substring(0, reader.getFilename().indexOf(".fastq"));
			} else if (reader.getFilename().contains(".fq")) {
				outputTemplate = reader.getFilename().substring(0, reader.getFilename().indexOf(".fq"));
			} else {
				outputTemplate = reader.getFilename();
			}
		}
		split();
	}
}
