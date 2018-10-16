package io.compgen.ngsutils.cli.fastq;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name = "fastq-check", desc = "Verify a FASTQ single, paired, or interleaved file(s)", category="fastq")
public class FastqCheck extends AbstractCommand {
    private String[] filenames;
    private String out1Filename;
    private String out2Filename;
	private boolean colorspace = false;

	public FastqCheck() {
	}

    @UnnamedArg(name = "FILE {FILE2}")
    public void setFilename(String[] filenames) throws IOException {
        this.filenames = filenames;
    }
    
    @Option(name="out1", desc="Write all valid reads (R1) to this file (filenames ending in .gz will be compressed)")
    public void setOut1(String filename) throws IOException {
        this.out1Filename = filename;
    }
    
    @Option(name="out2", desc="Write all valid reads (R2) to this file (paired only)")
    public void setOut2(String filename) throws IOException {
        this.out2Filename = filename;
    }
    
   @Option(name="colorspace", desc="Reads are in color-space (default: base-space)")
    public void setColorspace(boolean value) {
        this.colorspace = value;
    }

	@Exec
    public void exec() throws IOException, CommandArgumentException {
	    long[] counts;
	    if (filenames.length == 1) {
	        counts = execSingleFile(filenames[0]);
	    } else {
            if ((out1Filename != null && out2Filename == null) || (out1Filename == null && out2Filename != null)) {
	            throw new CommandArgumentException("Missing --out1 or --out2 (must use both for paired reads)");
	        }
	        counts = execPairedFiles(filenames[0], filenames[1]);
	    }
	    
	    if (counts[0] == -1) {
	        System.out.println("ERROR");
	        System.exit(1);
	    }
	    
	    System.out.println("OK " + counts[0] + " valid reads found.");
	    if (counts[1] > 0) {
	        System.out.println("WARNING " + counts[1] + " bad reads found.");
	    }
	}

	protected boolean checkPaired(FastqRead read1, FastqRead read2) {
	    if (read2 != null) {
            if (read1.getName().endsWith("/1") && read2.getName().endsWith("/2")) {
                String r1 = read1.getName().substring(0, read1.getName().length()-2);
                String r2 = read2.getName().substring(0, read2.getName().length()-2);
            
                if (!r1.equals(r2)) {
                    return false;
                }

            } else if (!read1.getName().equals(read2.getName())) {
                return false;
	        }
	    }
	    return true;
	}

	protected boolean checkSeqQualLength(FastqRead read) {
        if (colorspace) {
            if ((read.getSeq().length()+1) != read.getQual().length()) {
                // prefixed colorspace
                return false;
            }
        } else if (read.getSeq().length() != read.getQual().length()) {
            return false;
        }
        return true;
	}
	
	protected long[] execPairedFiles(String filename1, String filename2) throws IOException {
	    System.err.println("Reading files: "+filename1+", "+filename2);
        final FastqReader reader1 = Fastq.open(filename1);
        final FastqReader reader2 = Fastq.open(filename2, true);

        Iterator<FastqRead> it1 = reader1.iterator();
        Iterator<FastqRead> it2 = reader2.iterator();

        OutputStream out1 = null;
        OutputStream out2 = null;
        
        
        if (out1Filename != null && out2Filename != null) {
            if (out1Filename.endsWith(".gz")) {
                out1 = new GZIPOutputStream(new FileOutputStream(out1Filename));
            } else {
                out1 = new FileOutputStream(out1Filename);
            }

            if (out2Filename.endsWith(".gz")) {
                out2 = new GZIPOutputStream(new FileOutputStream(out2Filename));
            } else {
                out2 = new FileOutputStream(out2Filename);
            }
        }
        
        long count = 0;
        long errorCount = 0;
        
        while (it1.hasNext() && it2.hasNext()) {
            FastqRead one = it1.next();
            FastqRead two = it2.next();
            
            boolean isGood = checkPair(one, two);

            if (isGood) {
                count++;
                if (out1 != null) {
                    one.write(out1);
                    two.write(out2);
                }
            } else {
                if (out1 != null) {
                    errorCount++;
                } else {
                    reader1.close();
                    reader2.close();
                    return new long[]{-1,0};
                }
            }
        }
        
        reader1.close();
        reader2.close();

        if (out1!=null) {
            out1.flush();
            out2.flush();
            out1.close();
            out2.close();
        } else {
            // One reader still had reads...
            if (it1.hasNext() || it2.hasNext()) {
                return new long[]{-1,0};
            }
            
            // one reader stopped short
            if (reader1.hasWarning() || reader2.hasWarning()) {
                return new long[]{-1,0};
            }
        }
        
        return new long[] {count, errorCount};
	}

    protected boolean checkPair(FastqRead one, FastqRead two) {
        boolean isGood = true;

        if (!checkPaired(one, two)) {
            System.err.println("Unpaired read found! " + one.getName());
            isGood = false;
        }
        if (!checkSeqQualLength(one)) {
            System.err.println("Read seq/qual length mismatch! " + one.getName());
            isGood = false;
        }
        if (!checkSeqQualLength(two)) {
            System.err.println("Read seq/qual length mismatch! " + two.getName());
            isGood = false;
        }
        
        return isGood;
    }

    protected boolean checkSingle(FastqRead one) {
        boolean isGood = true;

        if (!checkSeqQualLength(one)) {
            System.err.println("Read seq/qual length mismatch! " + one.getName());
            isGood = false;
        }
        
        return isGood;
    }

	protected long[] execSingleFile(String filename) throws IOException {
        System.err.println("Reading file: "+filename);
		FastqReader reader = Fastq.open(filename);

        OutputStream out1 = null;
        
        if (out1Filename != null) {
            if (out1Filename.endsWith(".gz")) {
                out1 = new GZIPOutputStream(new FileOutputStream(out1Filename));
            } else {
                out1 = new FileOutputStream(out1Filename);
            }
        }
        
        long count = 0;
        long errorCount = 0;

        
        boolean paired = false;
        FastqRead lastRead = null;
        
        boolean inHeader = true;
        
        FastqRead first = null;
        FastqRead second = null;
		
		for (FastqRead read : reader) {
		    if (inHeader) {
		        if (first == null) {
		            first = read;
		        } else if (second == null) {
		            second = read;
		        }
		        
		        if (first != null && second != null) {
		            inHeader = false;
		            if (first.getName().equals(second.getName())) {
		                System.err.println("Interleaved reads");
		                paired = true;
		                boolean isGood = checkPair(first, second);
		                if (isGood) {
		                    count++;
	                        if (out1 != null) {
	                            first.write(out1);
	                            second.write(out1);
	                        }

		                } else {
		                    if (out1 != null) {
		                        errorCount++;
		                    } else {
		                        reader.close();
		                        return new long[] {-1,0};
		                    }
		                }
		            } else {
                        boolean isGood = checkSingle(first);
                        if (isGood) {
                            count++;
                            if (out1 != null) {
                                first.write(out1);
                            }
                        } else {
                            if (out1 != null) {
                                errorCount++;
                            } else {
                                reader.close();
                                return new long[] {-1,0};
                            }
                        }

                        isGood = checkSingle(second);
                        if (isGood) {
                            count++;
                            if (out1 != null) {
                                second.write(out1);
                            }
                        } else {
                            if (out1 != null) {
                                errorCount++;
                            } else {
                                reader.close();
                                return new long[] {-1,0};
                            }
                        }
		            }
		        }
		    } else {
		        if (paired) {
		            if (lastRead == null) {
		                lastRead = read;
		            } else {
                       boolean isGood = checkPair(lastRead, read);
                        if (isGood) {
                            count++;
                            if (out1 != null) {
                                lastRead.write(out1);
                                read.write(out1);
                            }
                        } else {
                            if (out1 != null) {
                                errorCount++;
                            } else {
                                reader.close();
                                return new long[] {-1,0};
                            }
                        }
                        lastRead = null;
		            }
		        } else {
                    boolean isGood = checkSingle(read);
                    if (isGood) {
                        count++;
                        if (out1 != null) {
                            read.write(out1);
                        }
                    } else {
                        if (out1 != null) {
                            errorCount++;
                        } else {
                            reader.close();
                            return new long[] {-1,0};
                        }
                    }
		        }
		    }
		}
		
		if (paired && lastRead != null) {
            System.err.println("Trailing read unpaired!");
            if (out1 != null) {
                errorCount++;
            } else {
                reader.close();
                return new long[] {-1,0};
            }
		}
    
        if (out1!=null) {
            out1.flush();
            out1.close();
        }

		reader.close();
		return new long[]{count, errorCount};
	}
}
