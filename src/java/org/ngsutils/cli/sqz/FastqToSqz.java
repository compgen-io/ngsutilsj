package org.ngsutils.cli.sqz;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.sqz.SQZ;
import org.ngsutils.sqz.SQZWriter;
import org.ngsutils.support.IterUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-sqz")
@Command(name = "fastq-sqz", desc = "Converts a FASTQ file (or two paired files) into a SQZ file", cat="sqz", experimental=true)
public class FastqToSqz extends AbstractCommand {
    private FastqReader[] readers = null;
	private String outputFilename = null;
	private boolean force = false;
	private boolean comments = false;

	private boolean noCompress = false;
	private boolean interleaved = false;
	
    @Unparsed(name="FILE1 FILE2")
    public void setFilenames(List<File> files) throws IOException {
        if (files.size() == 2) {
            this.readers = new FastqReader[2];
            this.readers[0] = new FastqReader(files.get(0));
            this.readers[1] = new FastqReader(files.get(1));
        } else if (files.size() == 1) {
            this.readers = new FastqReader[1];
            this.readers[0] = new FastqReader(files.get(0));
        } else {
            System.err.println("You must supply one or two FASTQ files to convert!");
            System.exit(1);
        }
    }

    @Option(description = "Output filename (Default: stdout)", shortName = "o", defaultValue="-", longName = "output")
    public void setOutputFilename(String outFilename) {
        this.outputFilename = outFilename;
    }
    @Option(description = "Force overwriting output file", longName = "force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(description = "Don't compress the SQZ file", longName = "no-compress")
    public void setNoCompress(boolean val) {
        this.noCompress = val;
    }
    
    @Option(description = "Input FASTQ is interleaved", longName = "interleaved")
    public void setInterleaved(boolean val) {
        this.interleaved = val;
    }
    
    @Option(description = "Include comments field from FASTQ file", longName = "comments")
    public void setComments(boolean val) {
        this.comments = val;
    }
    
	@Override
	public void exec() throws IOException, GeneralSecurityException {
	    if (readers == null) {
            throw new ArgumentValidationException("You must supply one or two FASTQ files to convert.");
	    }
	   	    
        if (verbose) {
            for (FastqReader reader: readers) {
                System.err.println("Input: "+reader.getFilename());
            }
            if (comments) {
                System.err.println("Including comments");
            }
            if (readers.length == 2) {
                System.err.println("Paired inputs");
            } else if (interleaved) {
                System.err.println("Interleaved input file");
            }
        }

        final SQZWriter out;
        int flags = 0;
        if (comments) {
            flags |= SQZ.HAS_COMMENTS;
        }
        if (readers.length==2 || interleaved) {
            flags |= SQZ.PAIRED;
        }
        if (outputFilename.equals("-")) {
            out = new SQZWriter(System.out, flags);
            if (verbose) {
                System.err.println("Output: stdout (uncompressed)");
            }
        } else {
            if (new File(outputFilename).exists() && !force) {
                throw new ArgumentValidationException("The output file: "+outputFilename+" exists! Use --force to overwrite.");
            }
            if (!noCompress) {
                flags |= SQZ.DEFLATE_COMPRESSED;
            }
            out = new SQZWriter(outputFilename, flags);
            if (verbose) {
                System.err.println("Output: "+outputFilename);
                System.err.println("Compress: " + (noCompress ? "no": "yes"));
            }
        }

        long i = 0;
        if (readers.length == 1 && !interleaved) {
            for (FastqRead read : readers[0]) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                }
                out.write(read);
            }
        } else if (readers.length == 1 && interleaved) {
            FastqRead buf = null;
            for (FastqRead read : readers[0]) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                }
                if (buf != null) {
                    out.writePair(buf, read);
                    buf = null;
                } else {
                    buf = read;
                }
            }
        } else {
            IterUtils.zip(readers[0], readers[1], new IterUtils.Each<FastqRead, FastqRead>() {
                long i = 0;
                public void each(FastqRead one, FastqRead two) {
                    if (verbose) {
                        i++;
                        if (i % 100000 == 0) {
                            System.err.println("Read: " + i);
                        }
                    }
                    try {
                        out.writePair(one, two);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            });
        }
        for (FastqReader reader: readers) {
            reader.close();
        }
        out.close();
	}
}

