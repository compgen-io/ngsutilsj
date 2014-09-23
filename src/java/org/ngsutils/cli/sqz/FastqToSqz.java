package org.ngsutils.cli.sqz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtils;
import org.ngsutils.cli.AbstractCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.sqz.SQZ;
import org.ngsutils.sqz.SQZWriter;
import org.ngsutils.support.IterUtils;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application = "ngsutilsj fastq-sqz")
@Command(name = "fastq-sqz", desc = "Converts a FASTQ file (or two paired files) into a SQZ file", cat="sqz", experimental=true)
public class FastqToSqz extends AbstractCommand {
    public class AnnotationValue {
        public final String name;
        public final String val;
        public final File file;
        public AnnotationValue(String name, File f) {
            this.name = name;
            this.file = f;
            this.val = null;
        }

        public AnnotationValue(String name, String val) {
            this.name = name;
            this.file = null;
            this.val = val;
        }
    }

    private FastqReader[] readers = null;
	private String outputFilename = null;
    private String password = null;
    private String passwordFile = null;

    private boolean useAES256 = false;
	private boolean force = false;
    private boolean comments = false;
    private boolean colorspace = false;

    private boolean compressDeflate = true;
    private boolean compressBzip2 = false;
	private boolean interleaved = false;
	
	private String curAnnName = null;
    private List<AnnotationValue> annValues = new ArrayList<AnnotationValue>();

	private int chunkSize = 10000;
	
    @Unparsed(name="FILE1 {FILE2}")
    public void setFilenames(List<String> files) throws IOException {
        if (files.size() > 0) {
            this.readers = new FastqReader[files.size()];
            for (int i=0; i<files.size(); i++) {
                this.readers[i] = new FastqReader(files.get(i));
            }
        } else {
            System.err.println("You must supply one or two FASTQ files to convert!");
            System.exit(1);
        }
    }

    @Option(description = "Output filename (Default: stdout)", shortName = "o", defaultValue="-", longName = "output")
    public void setOutputFilename(String outFilename) {
        this.outputFilename = outFilename;
    }

    @Option(description = "Text annotation name (default: none)", defaultToNull=true, longName = "ann-name")
    public void addAnnotationName(String text) {
        curAnnName = text;
    }

    @Option(description = "Text annotation value (default: none)", defaultToNull=true, longName = "ann-val")
    public void addAnnotationValue(String text) {
        if (text != null) {
            if (curAnnName == null) {
                curAnnName = "user";
            }
            this.annValues.add(new AnnotationValue(curAnnName, text));
            curAnnName = null;
        }
    }

    @Option(description = "Text annotation filename (default:none)", defaultToNull=true, longName = "ann-file")
    public void setTextFilename(String textFilename) {
        if (textFilename != null) {
            if (curAnnName == null) {
                curAnnName = "user";
            }
            File f = new File(textFilename);
            if (!f.exists()) {
                throw new ArgumentValidationException("The text annotation file: "+ textFilename+ " does not exist!");
            }
            this.annValues.add(new AnnotationValue(curAnnName, f));
            curAnnName = null;
        }
    }

    @Option(description = "Encryption password", longName = "pass", defaultToNull=true)
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(description = "File containing encryption password", longName = "pass-file", defaultToNull=true)
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }
    
    @Option(description = "Number of reads per compression/encryption block (default: 10000)", longName = "block-reads", defaultValue="10000")
    public void setChunkSize(int val) {
        this.chunkSize = val;
    }

    @Option(description = "Encrypt with AES-256 bit (may require special Java security policy) (default: AES-128 bit)", longName = "aes-256")
    public void setAES256(boolean val) {
        this.useAES256 = val;
    }


    
    @Option(description = "Input file is in colorspace", longName = "colorspace")
    public void setColorspace(boolean val) {
        this.colorspace = val;
    }

    @Option(description = "Force overwriting output file", longName = "force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(description = "Compress file using deflate algorithm (default)", longName = "deflate")
    public void setCompressDeflate(boolean val) {
        this.compressDeflate = val;
        this.compressBzip2 = !val;
    }
    
    @Option(description = "Compress file using bzip2 algorithm (smaller, slower)", longName = "bzip2")
    public void setCompressBzip2(boolean val) {
        this.compressBzip2 = val;
        this.compressDeflate = !val;
    }
    
    @Option(description = "Don't compress the SQZ file", longName = "no-compress")
    public void setNoCompress(boolean val) {
        this.compressDeflate = !val;
        this.compressBzip2 = !val;
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
        if (interleaved && readers.length > 1) {
            throw new ArgumentValidationException("You may not supply more than one FASTQ file in interleaved mode.");
        }

        if (password == null && passwordFile != null) {
            password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
        }
        
        if (verbose) {
            for (FastqReader reader: readers) {
                System.err.println("Input: "+reader.getFilename());
            }
            if (comments) {
                System.err.println("Including comments");
            }
            if (colorspace) {
                System.err.println("Input in colorspace");
            }
            if (readers.length > 1) {
                System.err.println("Paired inputs ("+readers.length+")");
            } else if (interleaved) {
                System.err.println("Interleaved input file");
            }
        }

        int flags = 0;
        if (comments) {
            flags |= SQZ.HAS_COMMENTS;
        }
        if (colorspace) {
            flags |= SQZ.COLORSPACE;
        }

        if (interleaved) {
            SQZWriter out = null;
            List<FastqRead> buffer = new ArrayList<FastqRead>();
            for (FastqRead read : readers[0]) {
                if (buffer.size() == 0) {
                    buffer.add(read);
                    continue;
                }

                boolean match = true;
                for (FastqRead test: buffer) {
                    if (!read.getName().equals(test.getName())) {
                        match = false;
                        break;
                    }
                }
                
                if (!match) {
                    if (out == null) {
                        out = buildSQZ(flags, buffer.size());
                    }
                    out.writeReads(buffer, verbose);
                    buffer.clear();
                }
                buffer.add(read);
            }
            if (buffer.size() > 0) {
                if (out == null) {
                    out = buildSQZ(flags, buffer.size());
                }
                out.writeReads(buffer, verbose);
                buffer.clear();
            }
            
            if (annValues.size()>0) {
                for (AnnotationValue ann: annValues) {
                    if (ann.val!=null) {
                        out.writeText(ann.name, ann.val);
                        if (verbose) {
                            System.err.println("Adding text annotation: [" + ann.name+"] " + ann.val);
                        }
                    } else if (ann.file!=null) {
                        out.writeText(ann.name, new FileInputStream(ann.file));
                        if (verbose) {
                            System.err.println("Adding text annotation:  [" + ann.name+"] " + ann.file.getName());
                        }
                    }                        
                }
            }
            
            out.close();
            if (verbose) {
                System.err.println("Data chunks: "+out.getChunkCount());
            }
        } else {
            final SQZWriter out = buildSQZ(flags, readers.length);
            IterUtils.zipArray(readers, new IterUtils.EachList<FastqRead>() {
                long i = 0;
                public void each(List<FastqRead> reads) {
                    if (verbose) {
                        i++;
                        if (i % 100000 == 0) {
                            System.err.println("Read: " + i);
                        }
                    }
                    try {
                        out.writeReads(reads, verbose);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            });
            if (annValues.size()>0) {
                for (AnnotationValue ann: annValues) {
                    if (ann.val!=null) {
                        out.writeText(ann.name, ann.val);
                        if (verbose) {
                            System.err.println("Adding text annotation: [" + ann.name+"] " + ann.val);
                        }
                    } else if (ann.file!=null) {
                        out.writeText(ann.name, new FileInputStream(ann.file));
                        if (verbose) {
                            System.err.println("Adding text annotation:  [" + ann.name+"] " + ann.file.getName());
                        }
                    }                        
                }
            }
            out.close();
            if (verbose) {
                System.err.println("Data blocks: "+out.getChunkCount());
            }
        }
        for (FastqReader reader: readers) {
            reader.close();
        }
	}

	private SQZWriter buildSQZ(int flags, int readCount) throws IOException, GeneralSecurityException {
	    SQZWriter out=null;
	    
        if (outputFilename.equals("-")) {
            out = new SQZWriter(System.out, flags, readCount, SQZ.COMPRESS_NONE, password == null ? null: (useAES256 ? "AES-256": "AES-128"), password);
            if (verbose) {
                System.err.println("Output: stdout (uncompressed)");
                System.err.println("Encryption: " + (password == null ? "no": (useAES256 ? "AES-256": "AES-128")));
            }
        } else {
            if (new File(outputFilename).exists() && !force) {
                throw new ArgumentValidationException("The output file: "+outputFilename+" exists! Use --force to overwrite.");
            }
            int compressionType;
            
            if (compressDeflate) {
                compressionType = SQZ.COMPRESS_DEFLATE;
            } else if (compressBzip2) {
                compressionType = SQZ.COMPRESS_BZIP2;
            } else {
                compressionType = SQZ.COMPRESS_NONE;
            }
            out = new SQZWriter(outputFilename, flags, readCount, compressionType, password == null ? null: (useAES256 ? "AES-256": "AES-128"), password);

            if (verbose) {
                System.err.println("Output: "+outputFilename);
                System.err.println("Encryption: " + (password == null ? "no": (useAES256 ? "AES-256": "AES-128")));
                System.err.println("Compression: " +compressionType);
            }
        }
        
        out.setChunkSize(chunkSize);
        
        if (verbose) {
            System.err.println("Reads per block: "+chunkSize);
        }

        out.writeText("SQZ", "{ \"version\": \"" + NGSUtils.getVersion()+ "\", \"cmdline\":\"" + NGSUtils.getArgs()+"\"}");
        
        return out;

	}
}

