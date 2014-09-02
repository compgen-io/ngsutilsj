package org.ngsutils.cli.sqz;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.zip.GZIPOutputStream;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.sqz.SQZReader;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj sqz-fastq")
@Command(name="sqz-fastq", desc="Export the read sequences from an SQZ file to FASTQ format", cat="sqz", experimental=true)
public class SqzToFastq extends AbstractCommand {
    
    private String filename=null;
    private String outTemplate=null;
    private String password = null;
    private String passwordFile = null;

    private boolean split = false;
    private boolean compress = false;
    private boolean force = false;
    private boolean ignoreComments = false;
    
    private boolean first = false;
    private boolean second = false;
    
    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Output filename template (default: stdout)", longName="out", defaultToNull=true)
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(description = "Decryption password", longName = "pass", defaultToNull=true)
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(description = "File containing decryption password", longName = "pass-file", defaultToNull=true)
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
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

    @Option(description = "Ouput only first reads (default: output both)", longName="first")
    public void setFirst(boolean val) {
        this.first = val;
    }

    @Option(description = "Ouput only second reads (default: output both)", longName="second")
    public void setSecond(boolean val) {
        this.second = val;
    }

    @Option(description = "Don't write comments (if present)", longName = "ignore-comments")
    public void setNoComments(boolean val) {
        this.ignoreComments = val;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException, GeneralSecurityException {        
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input FQA file!");
        }
        if (split && (outTemplate == null || outTemplate.equals("-"))) {
            throw new ArgumentValidationException("You cannot have split output to stdout!");
        }

        if (first && second) {
            throw new ArgumentValidationException("You can not use --first and --second at the same time!");
        }

        if (split && (first || second)) {
            throw new ArgumentValidationException("You can not use --split and --first or --second at the same time!");
        }
        if (password == null && passwordFile != null) {
            password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
        }
        
        SQZReader reader;
        if (filename.equals("-")) {
            reader = SQZReader.open(System.in, ignoreComments, password);
            if (verbose) {
                System.err.println("Input: stdin");
            }
        } else {
            reader = SQZReader.open(filename, ignoreComments, password);
            if (verbose) {
                System.err.println("Input: " + filename);
            }
        }

        OutputStream[] outs;
        if (outTemplate==null || outTemplate.equals("-")) {
            outs = new OutputStream[] { new BufferedOutputStream(System.out) };
            if (verbose) {
                System.err.println("Output: stdout");
            }
        } else if (outTemplate != null && (first || second)) {
            String outFilename;
            if (compress) {
                if (first) { 
                    outFilename = outTemplate+"_R1.fastq.gz";
                } else {
                    outFilename = outTemplate+"_R2.fastq.gz";
                }
            } else {
                if (first) { 
                    outFilename = outTemplate+"_R1.fastq";
                } else {
                    outFilename = outTemplate+"_R2.fastq";
                }
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

        long i=0;
        String lastName = null;

        for (FastqRead read: reader) {
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
            }
            if (split && read.getName().equals(lastName)) {
                read.write(outs[1]);
            } else {
                if (
                        (first && !read.getName().equals(lastName)) || 
                        (second && read.getName().equals(lastName)) || 
                        (!first && !second)
                    ) {
                    read.write(outs[0]);
                }
            }
            lastName = read.getName();
        }
        for (OutputStream out: outs) {
            out.close();
        }
        reader.close();
    }    
}
