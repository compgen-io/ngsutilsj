package org.ngsutils.cli.fastq;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMRecord;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;

import org.ngsutils.NGSUtils;
import org.ngsutils.fastq.Fastq;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.support.IterUtils;
import org.ngsutils.support.cli.AbstractCommand;
import org.ngsutils.support.cli.Command;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-bam")
@Command(name = "fastq-bam", desc = "Converts a FASTQ file (or two paired files) into an unmapped BAM file", cat="fastq")
public class FastqToBam extends AbstractCommand {
    private String[] filenames = null;
	private String outputFilename = null;
	private String tmpDir = null;
	private boolean calcMD5 = false;
	private boolean force = false;
	private boolean comments = false;
	private boolean serial = false;
	private int compressionLevel = 6; // sam.jar default is 5, but 6 is the standard default

	public FastqToBam() {
	}

    @Unparsed(name="FILE1 FILE2")
    public void setFilenames(List<String> files) throws IOException {
        if (files.size() == 2) {
            this.filenames = new String[2];
            this.filenames[0] = files.get(0);
            this.filenames[1] = files.get(1);
        } else if (files.size() == 1) {
            this.filenames = new String[1];
            this.filenames[0] = files.get(0);
        } else {
            System.err.println("You must supply one or two FASTQ files to convert!");
            System.exit(1);
        }
    }

    @Option(description = "Output filename (Default: stdout)", shortName = "o", defaultValue="-", longName = "output")
    public void setOutputFilename(String outFilename) {
        this.outputFilename = outFilename;
    }

    @Option(description = "Automatically write an MD5 file", longName = "md5")
    public void setCalcMD5(boolean val) {
        this.calcMD5 = val;
    }

    @Option(description = "Add paired FASTQ files serially, rather than interleaved", longName = "serial")
    public void setSerial(boolean val) {
        this.serial = val;
    }
    
    @Option(description = "Force overwriting output file", longName = "force")
    public void setForce(boolean val) {
        this.force = val;
    }
    
    @Option(description = "Compression-level: fast (1)", longName = "fast")
    public void setFast(boolean val) {
        if (val) {
            compressionLevel = 1;
        }
    }

    @Option(description = "Compression-level: best (9)", longName = "best")
    public void setBest(boolean val) {
        if (val) {
            compressionLevel = 9;
        }
    }

    @Option(description = "Include comments field from FASTQ file", longName = "comments")
    public void setComments(boolean val) {
        this.comments = val;
    }
    
    @Option(description = "Write temporary files here", longName="tmpdir", defaultToNull=true)
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

	@Override
	public void exec() throws IOException {
	    if (filenames == null) {
            throw new ArgumentValidationException("You must supply one or two FASTQ files to convert.");
	    }
	   	    
        SAMFileWriterFactory factory = new SAMFileWriterFactory();

        File outfile = null;
        OutputStream outStream = null;
        
        if (outputFilename.equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(outputFilename);
            if (outfile.exists() && !force) {
                System.err.println("The output file: "+outputFilename+" exists!\nYou must set the --force option to overwrite the output file.");
                System.exit(1);
            }
            if (calcMD5) {
                factory.setCreateMd5File(true);
            }
        }

        
        
        if (verbose) {
            for (String filename: filenames) {
                System.err.println("Input: "+filename);
            }
            if (comments) {
                System.err.println("Including comments");
            }
            if (compressionLevel == 1) {
                System.err.println("Compression: fast");
            }
            if (compressionLevel == 9) {
                System.err.println("Compression: best");
            }
        }

        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile!=null) {
            factory.setTempDirectory(outfile.getParentFile());
        }

        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SortOrder.unsorted);
        SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("fastq-bam");
        header.addProgramRecord(pg);

        final SAMFileWriter out;
        if (outfile != null) {
            if (verbose) {
                System.err.println("Output: "+outfile);
            }
            out = factory.makeBAMWriter(header, true, outfile, compressionLevel);
        } else {
            if (verbose) {
                System.err.println("Output: stdout");
            }
            out = factory.makeSAMWriter(header,  true,  outStream);
        }
        
        FastqReader[] readers = new FastqReader[filenames.length];
        for (int i=0; i<filenames.length; i++) {
            if (i==0) {
                File file1 = new File(filenames[i]);
                FileInputStream fis1 = new FileInputStream(file1);
                FileChannel channel1 = fis1.getChannel();

                readers[i] = Fastq.open(fis1, null, channel1, filenames[i]);
            } else {
                readers[i] = Fastq.open(filenames[i]);
            }
        }

        long i = 0;
        if (readers.length == 1) {
	        for (FastqRead read : readers[0]) {
	            if (verbose) {
	                i++;
	                if (i % 100000 == 0) {
	                    System.err.println("Read: " + i);
	                }
	                
	            }
	            SAMRecord record = new SAMRecord(header);
	            record.setReadPairedFlag(false);
	            record.setReadUnmappedFlag(true);
	            record.setReadName(read.getName());
	            record.setReadString(read.getSeq());
	            record.setBaseQualityString(read.getQual());
	            
	            if (comments && read.getComment() != null) {
	                record.setAttribute("CO", read.getComment());
	            }
	            
                out.addAlignment(record);
	        }
        } else if (serial) {
            for (FastqRead read : readers[0]) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                    
                }
                SAMRecord record = new SAMRecord(header);
                record.setReadPairedFlag(true);
                record.setMateUnmappedFlag(true);
                record.setReadUnmappedFlag(true);
                record.setFirstOfPairFlag(true);
                record.setSecondOfPairFlag(false);
                record.setReadName(read.getName());
                record.setReadString(read.getSeq());
                record.setBaseQualityString(read.getQual());

                if (comments && read.getComment() != null) {
                    record.setAttribute("CO", read.getComment());
                }
                
                out.addAlignment(record);
            }
            i = 0;
            for (FastqRead read : readers[1]) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                    
                }
                SAMRecord record = new SAMRecord(header);
                record.setReadPairedFlag(true);
                record.setMateUnmappedFlag(true);
                record.setReadUnmappedFlag(true);
                record.setFirstOfPairFlag(false);
                record.setSecondOfPairFlag(true);
                record.setReadName(read.getName());
                record.setReadString(read.getSeq());
                record.setBaseQualityString(read.getQual());

                if (comments && read.getComment() != null) {
                    record.setAttribute("CO", read.getComment());
                }
                
                out.addAlignment(record);
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
                    if (one.getName().equals(two.getName())) {
                        SAMRecord record = new SAMRecord(header);
                        record.setReadPairedFlag(true);
                        record.setMateUnmappedFlag(true);
                        record.setReadUnmappedFlag(true);
                        record.setFirstOfPairFlag(true);
                        record.setSecondOfPairFlag(false);
                        record.setReadName(one.getName());
                        record.setReadString(one.getSeq());
                        record.setBaseQualityString(one.getQual());
                        if (comments && one.getComment() != null) {
                            record.setAttribute("CO", one.getComment());
                        }
                        out.addAlignment(record);
                        
                        record = new SAMRecord(header);
                        record.setReadPairedFlag(true);
                        record.setMateUnmappedFlag(true);
                        record.setReadUnmappedFlag(true);
                        record.setFirstOfPairFlag(false);
                        record.setSecondOfPairFlag(true);
                        record.setReadName(two.getName());
                        record.setReadString(two.getSeq());
                        record.setBaseQualityString(two.getQual());
                        if (comments && two.getComment() != null) {
                            record.setAttribute("CO", two.getComment());
                        }
                        out.addAlignment(record);

                    } else {
                        System.err.println("Error! Unpaired files! ");
                        System.exit(1);
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

