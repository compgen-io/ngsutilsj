package org.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-fastq")
@Command(name="bam-fastq", desc="Export the read sequences from a BAM file in FASTQ format", cat="bam")
public class BamToFastq extends AbstractCommand {
    
    private String filename=null;
    private String outTemplate=null;

    private boolean split = false;
    private boolean compress = false;
    private boolean force = false;
    
    private boolean lenient = false;
    private boolean silent = false;

    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Output filename template (default: stdout)", longName="out", defaultToNull=true)
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(description = "Force overwriting output", longName="force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(description = "Compress output", longName="gz")
    public void setCompress(boolean val) {
        this.compress = val;
    }

    @Option(description = "Split output into paired files (default: interleaved)", longName="split")
    public void setSplit(boolean val) {
        this.split = val;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
    
    @Override
    public void exec() throws NGSUtilsException, IOException {        
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input BAM file!");
        }
        if (split && (outTemplate == null || outTemplate.equals("-"))) {
            throw new ArgumentValidationException("You cannot have split output to stdout!");
        }
        
        SAMFileReader reader;
        if (filename.equals("-")) {
            reader = new SAMFileReader(System.in);
        } else {
            reader = new SAMFileReader(new File(filename));
        }

        OutputStream[] outs;
        if (outTemplate==null || outTemplate.equals("-")) {
            outs = new OutputStream[] { new BufferedOutputStream(System.out) };
            if (verbose) {
                System.err.println("Output: stdout");
            }
        } else if (outTemplate != null && split) {
            String[] outFilenames;
            if (compress) {
                outFilenames = new String[] { outTemplate+"_R1.fastq.gz", outTemplate+"_R2.fastq.gz" };
            } else {
                outFilenames = new String[] {outTemplate+"_R1.fastq", outTemplate+"_R2.fastq"};
            }
            for (String outFilename: outFilenames) {
                if (new File(outFilename).exists() && !force) {
                    reader.close();
                    throw new ArgumentValidationException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
                }
                if (verbose) {
                    System.err.println("Output: " + outFilename);
                }
            }

            outs = new OutputStream[2];
            if (compress) {
                outs[0] = new GZIPOutputStream(new FileOutputStream(outFilenames[0]));
                outs[1] = new GZIPOutputStream(new FileOutputStream(outFilenames[1]));                
            } else {
                outs[0] = new BufferedOutputStream(new FileOutputStream(outFilenames[0]));
                outs[1] = new BufferedOutputStream(new FileOutputStream(outFilenames[1]));                
            }
        } else {
            String outFilename;
            if (compress) {
                outFilename = outTemplate+".fastq.gz";
            } else {
                outFilename = outTemplate+".fastq";
            }
            if (new File(outFilename).exists() && !force) {
                reader.close();
                throw new ArgumentValidationException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
            }
            if (verbose) {
                System.err.println("Output: " + outFilename);
            }
            if (compress) {
                outs = new OutputStream[] { new GZIPOutputStream(new FileOutputStream(outFilename)) };
            } else {
                outs = new OutputStream[] { new BufferedOutputStream(new FileOutputStream(outFilename)) };
            }
        }
        
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        long i=0;
        for (SAMRecord read: reader) {
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
                
            }
            
            if (read != null) {
                String name = read.getReadName();
                String seq;
                String qual;
                if (read.getReadNegativeStrandFlag()) {
                    seq = ReadUtils.revcomp(read.getReadString());
                    qual = StringUtils.reverse(read.getBaseQualityString());
                } else {
                    seq = read.getReadString();
                    qual = read.getBaseQualityString();
                }
                
                FastqRead fqRead = new FastqRead(name, seq, qual); 
                
                if (split && !read.getFirstOfPairFlag()) {
                    fqRead.write(outs[1]);
                } else {
                    fqRead.write(outs[0]);
                }
            }
        }
        reader.close();
        for (OutputStream out: outs) {
            out.close();
        }
    }    
}
