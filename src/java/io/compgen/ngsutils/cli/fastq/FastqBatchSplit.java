package io.compgen.ngsutils.cli.fastq;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.fastq.filter.FilteringException;

@Command(name = "fastq-batchsplit", desc = "Splits a FASTQ file based on lane/barcode values", category="fastq")
public class FastqBatchSplit extends AbstractCommand {
    private String nameSubstr1[] = null;
    private String nameSubstr2[] = null;
    private String readGroups[] = null;
    private String outputTemplate = null;
    private String unmatchedFname = null;
    private String configFile = null;
    
    private boolean force = false;
    private boolean compress = false;

    private String filename;
  
    public FastqBatchSplit() {
    }

    @UnnamedArg(name = "FILE", defaultValue="-")
    public void setFilename(String filename) throws CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing file!");
        }
        if (!filename.equals("-")) {
            if (!new File(filename).exists()) {
                throw new CommandArgumentException("Missing file: "+filename);
            }
        }
        this.filename = filename;
    }

    @Option(desc="Read --rg-id, --str1, and --str2 values from a tab-delimited text file (rgid\\tstr1\\tstr2\\n)", name="conf", helpValue="fname")
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    @Option(desc="Read name contains this substring (e.g. flowcell/lane ID)", name="str1", helpValue="val1,val2,...")
    public void setNameSubstr1(String nameSubstr1) {
        this.nameSubstr1 = nameSubstr1.split(",");
    }

    @Option(desc="Read name contains this secondary substring (e.g. barcode)", name="str2", helpValue="val1,val2,...")
    public void setNameSubstr2(String nameSubstr2) {
        this.nameSubstr2 = nameSubstr2.split(",");
    }

    @Option(desc="Read group IDs (default: 0,1,2...)", name="rg-id", helpValue="val1,val2,...")
    public void setReadGroups(String readGroups) {
        this.readGroups = readGroups.split(",");
    }

    @Option(desc="Unmatched filename (for reads that aren't assigned to another RG)", name="unmatched", helpValue="fname")
    public void setUnmatchedFname(String unmatchedFname) {
        this.unmatchedFname = unmatchedFname;
    }

    @Option(desc="Output filename template (%RGID will be replaced with --rg-id values)", name="out", helpValue="template")
    public void setOutputTemplate(String outputTemplate) {
        this.outputTemplate = outputTemplate;
    }

    @Option(desc="Compress output files (gzip)", charName="z", name="gzip")
    public void setCompress(boolean compress) {
        this.compress = compress;
    }
    
    @Option(desc="Force overwriting existing files", charName="f", name="force")
    public void setForce(boolean force) {
        this.force = force;
    }
    
    @Exec
    public void exec() throws IOException, CommandArgumentException, FilteringException {
        if (configFile != null) {
            if (nameSubstr1 != null || nameSubstr2 != null || readGroups != null) {
                throw new CommandArgumentException("--rg-id, --str1, and --str2 values obtained from config file: " + configFile);
            }
            
            List<String> tmpRgID = new ArrayList<String>();
            List<String> tmpStr1 = new ArrayList<String>();
            List<String> tmpStr2 = new ArrayList<String>();
            
            StringLineReader reader = new StringLineReader(configFile);
            
            for (String line: reader) {
                if (line.charAt(0) == '#') {
                    continue;
                }
                if (StringUtils.strip(line).length() > 0) {
                    String[] cols = StringUtils.rstrip(line).split("\t");
                    if (cols.length == 0) {
                        continue;
                    }
                    
                    tmpRgID.add(cols[0]);
                    tmpStr1.add(cols[1]);
                    if (cols.length > 2) {
                        tmpStr2.add(cols[2]);
                    }
                }
            }
            
            reader.close();

            readGroups = tmpRgID.toArray(new String[]{});
            nameSubstr1 = tmpStr1.toArray(new String[]{});
            if (tmpStr2.size()>0) {
                nameSubstr2 = tmpStr2.toArray(new String[]{});
            }
        }
        
        if (filename == null) {
            throw new CommandArgumentException("Missing input filename!");
        }

        if (outputTemplate == null && unmatchedFname == null) {
            throw new CommandArgumentException("Missing --out filename template or --missing filename (at least one is required)!");
        }

        if (nameSubstr1 == null) {
            throw new CommandArgumentException("You must specify at least one value to split reads (-str1)!");
        }

        if (readGroups == null) {
            readGroups = new String[nameSubstr1.length];
            for (int i=0; i<nameSubstr1.length; i++) {
                readGroups[i] = ""+i;
            }
        }
        
        if (nameSubstr1.length != readGroups.length) {
            throw new CommandArgumentException("--str1 and --rg-id must be the same length!");
        }
        
        if (nameSubstr2 != null && nameSubstr1.length != nameSubstr2.length) {
            throw new CommandArgumentException("--str1 and --str2 must be the same length (if --str2 is set)!");
        }
        
        if (!force) {
            if (outputTemplate!=null) {
                for (int i=0; i<readGroups.length; i++) {
                    String fname = outputTemplate.replace("%RGID", readGroups[i]);
                    if (new File(fname).exists() && !force) {
                        throw new CommandArgumentException("File: "+fname+" exists! (Use -f to force overwriting)");
                    }
                }
            }
            if (unmatchedFname != null && new File(unmatchedFname).exists()) {
                throw new CommandArgumentException("File: "+unmatchedFname+" exists! (Use -f to force overwriting)");
            }
        }

        long[] counts = new long[readGroups.length];
        long unmatchedCount = 0;
        
        OutputStream[] outs = null;
        
        if (outputTemplate != null) {
            outs = new OutputStream[readGroups.length];
        
            for (int i=0; i<readGroups.length; i++) {
                String fname = outputTemplate.replace("%RGID", readGroups[i]);
                if (compress) {
                    outs[i] = new GZIPOutputStream(new FileOutputStream(fname));
                } else {
                    outs[i] = new FileOutputStream(fname);
                }
            }
        }

        OutputStream unmatched = null;
        if (unmatchedFname != null) {
            if (compress) {
                unmatched = new GZIPOutputStream(new FileOutputStream(unmatchedFname));
            } else {
                unmatched = new FileOutputStream(unmatchedFname);
            }
        }
        
        FastqReader reader = Fastq.open(filename);

        if (verbose) {
            System.err.println("Reading file:" + filename);
        }

        for (FastqRead read: reader) {
            boolean match = false;
            String fqLine = read.getName() + " " + read.getComment(); 
            for (int i=0; i<nameSubstr1.length && !match; i++) {
                if (fqLine.contains(nameSubstr1[i])) {
                    if (nameSubstr2 == null || fqLine.contains(nameSubstr2[i])) {
                        match = true;
                        counts[i]++;
                        if (outs != null) {
                            read.write(outs[i]);
                        }
                    }
                }
            }
            
            if (!match) {
                unmatchedCount++;
                if (unmatched != null) {
                    read.write(unmatched);
                }
            }
        }
        
        if (outs != null) {
            for (OutputStream out: outs) {
                out.flush();
                out.close();
            }
        }
        
        if (unmatched != null) {
            unmatched.flush();
            unmatched.close();
            if (unmatchedCount == 0) {
                // no unmatched reads, remove the file.
                File f = new File(unmatchedFname);
                if (f.exists()) {
                    f.delete();
                }
            }
        }
        
        for (int i=0; i<readGroups.length; i++) {
            System.err.println("Group " + readGroups[i] + ": "+counts[i]+" reads");
        }
        System.err.println("Unmatched: " + unmatchedCount +" reads");
        
    }
}
