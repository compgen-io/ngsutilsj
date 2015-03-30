package io.compgen.ngsutils.cli.fastq;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.Counter;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

import java.io.IOException;

@Command(name = "fastq-separate", desc = "Splits an interleaved FASTQ file by read number.", category="fastq")
public class FastqSeparate extends AbstractOutputCommand {
	private String filename = null;
	
	private boolean readOne = false;
	private boolean readTwo = false;

	public FastqSeparate() {
	}

	@UnnamedArg(name = "FILE")
	public void setFilename(String filename) throws IOException {
	    this.filename = filename;
	}

	@Option(desc="Export read 1", charName = "1", name="read1")
	public void setReadOne(boolean value) {
		this.readOne = value;
	}

	@Option(desc="Export read 2", charName = "2", name="read2")
	public void setReadTwo(boolean value) {
		this.readTwo = value;
	}

    @Exec
	public void exec() throws IOException, CommandArgumentException {
        if ((!readOne && !readTwo) || (readOne && readTwo)) {
            throw new CommandArgumentException("You must specify at one (and only one) read to export (-1 or -2)");
        }
        
		if (verbose) {
			System.err.println("Spliting file:" + filename);
            if (readOne) {
				System.err.println("Exporting read 1");
			} else {
				System.err.println("Exporting read 2");
			}
		}

	    FastqReader reader = Fastq.open(filename);
		
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
		reader.close();
		close();
	}

}
