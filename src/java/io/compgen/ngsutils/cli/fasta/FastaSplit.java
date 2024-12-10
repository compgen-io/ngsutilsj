package io.compgen.ngsutils.cli.fasta;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.ngsutils.tabix.BGZipOutputStream;

@Command(name="fasta-split", desc="Split a FASTA file into a new file for each sequence or a number of sequences", category="fasta")
public class FastaSplit extends AbstractCommand {
    
    private String filename = null;
    private String template = "";
    private Set<String> valid = null;
    private int maxSeqPerFile = -1;
    private boolean gzip = false;

    @Option(desc="Output template (new files will be named: template${name}.fa or template${num}.fa , set to - for stdout)", name="template")
    public void setTemplate(String template) {
        this.template = template;
    }    

    @Option(desc="Compress output with bgzip", name="gz")
    public void setGzip(boolean gzip) {
        this.gzip = gzip;
    }    

    @Option(desc="Split into files of N sequences per file (default: split by each sequence)", name="split-count")
    public void setMaxSeqPerFile(int maxSeqPerFile) {
        this.maxSeqPerFile = maxSeqPerFile;
    }    

    @UnnamedArg(name = "FILE [seq1 seq2...]")
    public void setFilename(String[] filename) throws CommandArgumentException {
        this.filename = filename[0];
        if (filename.length > 1) {
        	valid = new HashSet<String>();
        	for (int i=1; i<filename.length; i++) {
        		valid.add(filename[i]);
        	}
        }
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
                
        StringLineReader reader = new StringLineReader(filename);
        OutputStream bos = null;
        
        int seqCount = 0;
        int fileCount = 0;
        boolean isValidSeq = false;
        
        for (String line: IterUtils.wrap(reader.iterator())) {
            if (line.charAt(0) == '>') {
                String name = line.substring(1).split("\\s",2)[0];
                                
                if (valid == null || valid.contains(name)) {
                    seqCount++;
                    isValidSeq = true;

                    if (template.equals("-")) {
                		bos = System.out;
                	} else {
                		if (maxSeqPerFile > 0) {
                			if (bos == null || seqCount > maxSeqPerFile) {
                                if (bos != null) {
                            		if (bos != System.out) {
                            			bos.close();
                            		}
                                }
                                fileCount++;
                				if (gzip) {
                        			bos = new BufferedOutputStream(new BGZipOutputStream(template+fileCount+".fa.gz"));
                				} else {
                        			bos = new BufferedOutputStream(new FileOutputStream(template+fileCount+".fa"));
                				}
                    			seqCount = 1;
                			}
                		} else {
                            if (bos != null) {
                        		if (bos != System.out) {
                        			bos.close();
                        		}
                            }
                			if (gzip) {
                				bos = new BufferedOutputStream(new BGZipOutputStream(template+name+".fa.gz"));
                			} else {
                				bos = new BufferedOutputStream(new FileOutputStream(template+name+".fa"));
                			}
                		}
                	}
                    if (verbose) {
                    	System.err.println(name);
                    }
                } else {
                	bos = null;
                	isValidSeq = false;
                	if (verbose) {
                		System.err.println(name + " (skip)");
                	}
                }
            }
            if (isValidSeq) {
            	bos.write((line+"\n").getBytes());
            }
        }
        
        reader.close();

        if (bos != null) {
        	bos.flush();
    		if (bos != System.out) {
    			bos.close();
    		}
        }
    }
}
