package io.compgen.ngsutils.cli.fastq;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

@Command(name = "fastq-split", desc = "Splits an FASTQ file into smaller files", category="fastq")
public class FastqSplit extends AbstractCommand {
	private String filename;

	private String outputTemplate = null;
	private boolean compressOuput = false;
	private int num = 2;

	public FastqSplit() {
	}

	@UnnamedArg(name = "FILE")
	public void setFilename(String filename) throws IOException {
	    this.filename = filename;
	}

	@Option(desc="Number of subfiles to split into", charName = "n", name="num")
	public void setNum(int num) {
		this.num = num;
	}

	@Option(desc="Output filename template", charName = "o", name="output")
	public void setOutputTemplate(String outputTemplate) throws IOException {
		this.outputTemplate = outputTemplate;
	}

	@Option(desc="Compress output (default: false)", charName = "z", name="compress")
	public void setCompressOuput(boolean compressOuput) {
		this.compressOuput = compressOuput;
	}

	@Exec
	public void exec() throws IOException, CommandArgumentException {
        if (outputTemplate == null) {
            if (filename.equals("-")) {
                throw new CommandArgumentException("You must specify an output template if reading from stdin");
            }
            if (filename.contains(".fastq")) {
                outputTemplate = filename.substring(0, filename.indexOf(".fastq"));
            } else if (filename.contains(".fq")) {
                outputTemplate = filename.substring(0, filename.indexOf(".fq"));
            } else {
                outputTemplate = filename;
            }
        }
		final OutputStream[] outs = new OutputStream[num];
		for (int i=0; i<num; i++) {
			if (compressOuput) {
				outs[i] = new GZIPOutputStream(new FileOutputStream(outputTemplate+"."+i+".fastq.gz"));

			} else {
				outs[i] = new BufferedOutputStream(new FileOutputStream(outputTemplate+"."+i+".fastq"));
			}
		}

		if (verbose) {
			System.err.println("Spliting file:" + filename);
			System.err.println("Output template:" + outputTemplate);
		}

		FastqReader reader = Fastq.open(filename);
		
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
		reader.close();
		for (OutputStream out: outs) {
		    out.close();
		}
	}
}
