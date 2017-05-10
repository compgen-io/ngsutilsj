package io.compgen.ngsutils.cli.fastq;

import java.io.IOException;
import java.io.OutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.Counter;
import io.compgen.common.io.FileUtils;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name = "fastq-separate", desc = "Splits an interleaved FASTQ file by read number.", category="fastq")
public class FastqSeparate extends AbstractCommand {
	private String filename = null;
	
    private String outfile1 = null;
    private String outfile2 = null;
    private boolean quiet = false;
    
	public FastqSeparate() {
	}

	@UnnamedArg(name = "fastqfile")
	public void setFilename(String filename) throws IOException {
	    this.filename = filename;
	}

    @Option(desc="Quiet output", charName="q")
    public void setQuiet(boolean val) {
        this.quiet = val;
    }

    @Option(desc="Export read 1 to this file (use - for stdout)", name="read1", required=false)
    public void setReadOne(String outfile1) {
        this.outfile1 = outfile1;
    }

    @Option(desc="Export read 2 to this file (use - for stdout)", name="read2", required=false)
    public void setReadTwo(String outfile2) {
        this.outfile2 = outfile2;
    }

    @Exec
	public void exec() throws IOException, CommandArgumentException {
        if (outfile1 == null && outfile2 == null) {
            throw new CommandArgumentException("You must specify at least one read to export");
        }

        if ((outfile1 != null && outfile1.equals("-")) && (outfile2 != null && outfile2.equals("-"))) {
            throw new CommandArgumentException("Both reads can not be exported to stdout!");
        }
        
        OutputStream out1 = null;
        OutputStream out2 = null;
                
		if (verbose) {
			System.err.println("Spliting file:" + filename);
		}
        if (outfile1 != null) {
            if (verbose) {
                System.err.println("Exporting read 1 to " + outfile1);
            }
            out1 = FileUtils.openOutputStream(outfile1);
        }
        if (outfile2 != null) {
            if (verbose) {
                System.err.println("Exporting read 2 to " + outfile2);
            }
            out2 = FileUtils.openOutputStream(outfile2);
		}

	    FastqReader reader = Fastq.open(filename, quiet);
		
		Counter counter = new Counter();
		boolean isRead1 = true;
		for (FastqRead read : reader) {
		    if (isRead1) {
	            counter.incr();
		    }
			if (out1 != null && isRead1) {
				read.write(out1);
			} else if (out2 != null && !isRead1) {
				read.write(out2);
			}
			isRead1 = !isRead1;
		}
		
		if (verbose) {
			System.err.println("Total reads: " + counter.getValue());
		}
		reader.close();
        if (out1 != null && out1 != System.out) {
            out1.close();
        }
        if (out2 != null && out2 != System.out) {
            out2.close();
        }
	}

}
