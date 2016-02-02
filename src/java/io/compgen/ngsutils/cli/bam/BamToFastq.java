package io.compgen.ngsutils.cli.bam;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.bam.BamFastqReader;
import io.compgen.ngsutils.fastq.FastqRead;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * TODO: Add option for exporting arbitrary tag values as comments (e.g. RG:Z, BC:Z, etc...)
 * TODO: Add option for exporting by Read Group
 * @author mbreese
 *
 */

@Command(name="bam-tofastq", desc="Export the read sequences from a BAM file in FASTQ format", category="bam")
public class BamToFastq extends AbstractCommand {
    
    private String filename=null;
    private String outTemplate=null;
    private String readGroup=null;

    private boolean allReadGroups = false;
    private boolean split = false;
    private boolean compress = false;
    private boolean force = false;
    private boolean comments = false;
    
    private boolean onlyFirst = false;
    private boolean onlySecond = false;
    private boolean mapped = false;
    private boolean unmapped = false;
    
    private boolean readNameSorted = false;
    private boolean singleEnded = false;
    
    private boolean lenient = false;
    private boolean silent = false;

    @UnnamedArg(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Output filename template (default: stdout)", name="out", helpValue="templ")
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(desc="Export all read-groups (must specify --out, read-group will be appended to output template)", name="all-rg")
    public void setAllReadGroups(boolean allReadGroups) {
        this.allReadGroups = allReadGroups;
    }

    @Option(desc="Export only this read group (ID)", name="rg")
    public void setReadGroup(String readGroup) {
        this.readGroup = readGroup;
    }

    @Option(desc="Force overwriting output", name="force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(desc="Input file is sorted by read-name (more memory efficient for exporting mapped reads)", name="name-sorted")
    public void setReadNameSorted(boolean val) {
        this.readNameSorted = val;
    }

    @Option(desc="Input file is single-end", name="single")
    public void setSingleEnded(boolean val) {
        this.singleEnded = val;
    }

    @Option(desc="Compress output", name="gz")
    public void setCompress(boolean val) {
        this.compress = val;
    }

    @Option(desc="Split output into paired files (default: interleaved)", name="split")
    public void setSplit(boolean val) {
        this.split = val;
    }

    @Option(desc="Output only first reads (default: output both)", name="first")
    public void setFirst(boolean val) {
        this.onlyFirst = val;
    }

    @Option(desc="Output only second reads (default: output both)", name="second")
    public void setSecond(boolean val) {
        this.onlySecond = val;
    }

    @Option(desc="Export mapped reads", name="mapped")
    public void setMapped(boolean val) {
        this.mapped = val;
    }

    @Option(desc="Export unmapped reads", name="unmapped")
    public void setUnmapped(boolean val) {
        this.unmapped = val;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
   
    @Option(desc="Include comments tag from BAM file", name="comments")
    public void setComments(boolean val) {
        this.comments = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM file!");
        }
        if (split && (outTemplate == null || outTemplate.equals("-"))) {
            throw new CommandArgumentException("You cannot have split output to stdout!");
        }

        if ((outTemplate == null || outTemplate.equals("-")) && allReadGroups) {
            throw new CommandArgumentException("Exporting all read groups to stdout is not supported!");
        }

        if (onlyFirst && onlySecond) {
            throw new CommandArgumentException("You can not use --first and --second at the same time!");
        }

        if (!mapped && !unmapped) {
            throw new CommandArgumentException("You aren't outputting any reads (--unmapped or --mapped (or both) must be set)!");
        }

        if (split && (onlyFirst || onlySecond)) {
            throw new CommandArgumentException("You can not use --split and --first or --second at the same time!");
        }
        
        OutputStream[] outs;
        Map<String, OutputStream> rgOuts = null;
        if (outTemplate==null || outTemplate.equals("-")) {
            outs = new OutputStream[] { new BufferedOutputStream(System.out) };
            if (verbose) {
                System.err.println("Output: stdout");
            }
        } else if (allReadGroups) {
            outs = null;
            rgOuts = new HashMap<String, OutputStream>();
        } else {
            if (onlyFirst) {
                outs = new OutputStream[] { buildOutputStream(true, false, null) }; 
            } else if (onlySecond) {
                outs = new OutputStream[] { buildOutputStream(false, true, null) };
            } else if (split) {
                outs = new OutputStream[] { 
                        buildOutputStream(true, false, null), 
                        buildOutputStream(false, true, null) 
                        };
            } else {
                outs = new OutputStream[] { buildOutputStream(true, true, null) };
            }
        }


        BamFastqReader bfq = new BamFastqReader(filename);
        bfq.setLenient(lenient);
        bfq.setSilent(silent);
        bfq.setFirst(!onlySecond);
        bfq.setSecond(!onlyFirst);
        bfq.setComments(comments);
        bfq.setIncludeUnmapped(unmapped);
        bfq.setIncludeMapped(mapped);
        if (!readNameSorted) {
            bfq.setDeduplicate(mapped); // if we only have mapped reads, we need to deduplicate
        } else {
            bfq.setDeduplicate(false);
        }
        
        
        String lastName = null;
        long i=0;
        int readCount = 0;
        for (FastqRead read: bfq) {
            if (readGroup != null && !readGroup.equals(read.getAttribute("RGID"))) {
                continue;
            }
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
            }

            if (read.getName().equals(lastName)) {
                if (singleEnded) {
                    continue;
                } else {
                    readCount++;
                    if (readCount > 2) {
                        continue;
                    }
                }
            } else {
                readCount = 1;
            }

            if (allReadGroups) {
                OutputStream out = null;
                if (split) {
                    if (read.getName().equals(lastName)) {
                        String key = read.getAttribute("RGID")+"_R2";
                        
                        if (!rgOuts.containsKey(key)) {
                            out = buildOutputStream(false, true, read.getAttribute("RGID"));
                            rgOuts.put(key,  out);
                        } else {
                            out = rgOuts.get(key);
                        }
                    } else {
                        String key = read.getAttribute("RGID")+"_R1";
                        
                        if (!rgOuts.containsKey(key)) {
                            out = buildOutputStream(true, false, read.getAttribute("RGID"));
                            rgOuts.put(key,  out);
                        } else {
                            out = rgOuts.get(key);
                        }
                    }
                } else {
                    String key = read.getAttribute("RGID");
                    
                    if (!rgOuts.containsKey(key)) {
                        out = buildOutputStream(true, true, read.getAttribute("RGID"));
                        rgOuts.put(key,  out);
                    } else {
                        out = rgOuts.get(key);
                    }
                }
                read.write(out);
                
            } else if (split && read.getName().equals(lastName)) {
                read.write(outs[1]);
            } else {
                read.write(outs[0]);
            }
            lastName = read.getName();
        }
        bfq.close();

        if (outs != null) {
            for (OutputStream out: outs) {
                out.close();
            }
        } else {
            for (OutputStream out: rgOuts.values()) {
                out.close();
            }
        }
        
    }    

    private OutputStream buildOutputStream(boolean read1, boolean read2, String readGroup) throws CommandArgumentException, IOException {
        String outFilename = outTemplate;
        
        if (readGroup != null) {
            outFilename += "_RG"+readGroup;
        }
        
        if (read1 && !read2) {
            outFilename += "_R1";
        } else if (read2 && !read1) {
            outFilename += "_R2";
        }
        
        if (compress) {
            outFilename += ".fastq.gz";
        } else {
            outFilename += ".fastq";
        }
        
        if (new File(outFilename).exists() && !force) {
            throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
        }

        if (verbose) {
            System.err.println("Output: " + outFilename);
        }

        if (compress) {
            return new GZIPOutputStream(new FileOutputStream(outFilename));
        } else {
            return new BufferedOutputStream(new FileOutputStream(outFilename));
        }

    }
    
}
