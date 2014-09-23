package org.ngsutils.cli.fastq;

import java.io.IOException;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.Fastq;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.support.Counter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-separate")
@Command(name = "fastq-separate", desc = "Splits an interleaved FASTQ file by read number.", cat="fastq")
public class FastqSeparate extends AbstractOutputCommand {
	private String filename = null;
	
	private boolean readOne = false;
	private boolean readTwo = false;

	public FastqSeparate() {
	}

	@Unparsed(name = "FILE")
	public void setFilename(String filename) throws IOException {
	    this.filename = filename;
	}

	@Option(description = "Export read 1", shortName = "1", longName="read1")
	public void setReadOne(boolean value) {
		this.readOne = value;
	}

	@Option(description = "Export read 2", shortName = "2", longName="read2")
	public void setReadTwo(boolean value) {
		this.readTwo = value;
	}

    @Override
	public void exec() throws IOException {
        if ((!readOne && !readTwo) || (readOne && readTwo)) {
            throw new NGSUtilsException("You must specify at one (and only one) read to export (-1 or -2)");
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
