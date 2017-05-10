package io.compgen.ngsutils.cli.fastq;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.Counter;
import io.compgen.common.IterUtils;
import io.compgen.common.IterUtils.EachPair;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name = "fastq-split", desc = "Splits an FASTQ file into smaller files", category="fastq")
public class FastqSplit extends AbstractCommand {
	private String[] filenames;
    private boolean ignoreReadNum = false;

	private String outputTemplate = null;
	private boolean compressOuput = false;
	private int num = 2;

	public FastqSplit() {
	}

	@UnnamedArg(name = "FILE {FILE2}")
	public void setFilename(String[] filenames) throws IOException {
	    this.filenames = filenames;
	}

	@Option(desc="Number of subfiles to split into", charName = "n", name="num")
	public void setNum(int num) {
		this.num = num;
	}

	@Option(desc="Output filename template (files will be named output.N.fastq{.gz})", charName = "o", name="output")
	public void setOutputTemplate(String outputTemplate) throws IOException {
		this.outputTemplate = outputTemplate;
	}

	@Option(desc="Compress output (default: false)", charName = "z", name="compress")
	public void setCompressOuput(boolean compressOuput) {
		this.compressOuput = compressOuput;
	}

	@Option(desc="Ignore Illumina read numbers in read names (/1, /2)", name="ignore-readnum")
    public void setIgnoreReadNum(boolean value) {
        this.ignoreReadNum = value;
    }

	@Exec
	public void exec() throws IOException, CommandArgumentException {
	    if (filenames.length != 1 && filenames.length != 2) {
	        throw new CommandArgumentException("You must specify an one or two input files");
	    }
        if (outputTemplate == null) {
            if (filenames[0].equals("-")) {
                throw new CommandArgumentException("You must specify an output template if reading from stdin");
            }
            if (filenames[0].contains(".fastq")) {
                outputTemplate = filenames[0].substring(0, filenames[0].indexOf(".fastq"));
            } else if (filenames[0].contains(".fq")) {
                outputTemplate = filenames[0].substring(0, filenames[0].indexOf(".fq"));
            } else {
                outputTemplate = filenames[0];
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
		    if (filenames.length > 1) {
                System.err.println("Merging and splitting files:" + filenames[0] + " " + filenames[1]);
		    } else {
                System.err.println("Spliting file:" + filenames[0]);
		    }
			
			System.err.println("Output template:" + outputTemplate);
		}

		if (filenames.length == 1) {
    		FastqReader reader = Fastq.open(filenames[0]);
    		
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
		} else {
	        FastqReader[] readers = new FastqReader[2];
	        File file1 = new File(filenames[0]);
	        FileInputStream fis1 = new FileInputStream(file1);
	        FileChannel channel1 = fis1.getChannel();

	        File file2 = new File(filenames[1]);
	        FileInputStream fis2 = new FileInputStream(file2);

	        readers[0] = Fastq.open(fis1, null, channel1, filenames[0]);
	        readers[1] = Fastq.open(fis2, null, null, filenames[1]);
	        
	        final Counter counter = new Counter();
	        
	        IterUtils.zip(readers[0], readers[1], new EachPair<FastqRead, FastqRead>() {
	            int i = -1;
	            public void each(FastqRead one, FastqRead two) {
	                counter.incr();
	                String n1 = one.getName(); 
	                String n2 = two.getName();
	                
	                if (ignoreReadNum) {
	                    if (n1.endsWith("/1")) {
	                        n1 = n1.substring(0, n1.length()-2);
	                        one.setName(n1);
	                        if (one.getComment() != null) {
	                            one.setComment("/1 " + one.getComment());
	                        } else {
	                            one.setComment("/1");
	                        }
	                    }
	                    if (n2.endsWith("/2")) {
	                        n2 = n2.substring(0, n2.length()-2);
	                        two.setName(n2);
	                        if (two.getComment() != null) {
	                            two.setComment("/2 " + two.getComment());
	                        } else {
	                            two.setComment("/2");
	                        }
	                    }
	                }
	                
	                if (n1.equals(n2)) {
	                    i++;
	                    if (i >= num) {
	                        i = 0;
	                    }

	                    try {
	                        one.write(outs[i]);
	                        two.write(outs[i]);
	                    } catch (IOException e) {
	                        System.err.println(e);
	                        System.exit(1);
	                    }
	                } else {
	                    System.err.println("Error! Unpaired files! (Read: "+counter.getValue()+", Expected: "+one.getName()+", Got: "+two.getName()+")");
	                    System.exit(1);
	                }
	            }
	        });
	        if (verbose) {
	            System.err.println("Total reads: "+counter.getValue());
	        }
	        readers[0].close();
	        readers[1].close();
		}
        for (OutputStream out: outs) {
            out.close();
        }
	}
}
