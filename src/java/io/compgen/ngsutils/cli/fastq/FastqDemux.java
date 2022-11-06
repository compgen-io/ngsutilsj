package io.compgen.ngsutils.cli.fastq;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
import io.compgen.ngsutils.support.DigestCmd;

@Command(name = "fastq-demux", desc = "Splits a FASTQ file based on lane/barcode values", category="fastq")
public class FastqDemux extends AbstractCommand {
    private String nameSubstr1[] = null;
    private String nameSubstr2[] = null;
    private String readGroups[] = null;
    private String outputTemplate = null;
    private String unmatchedFname = null;
    private String configFile = null;
    
    private int mismatches = 0;
    
    private boolean calcMD5 = false;
    private boolean calcSHA1 = false;
    private boolean force = false;
    private boolean compress = false;
    
    private boolean allowWildcards = false;

    private String filename;
  
    public FastqDemux() {
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

    @Option(desc="Read --rg-id, --lane, and --barcode values from a tab-delimited text file (rgid\\tlane\\tbarcode\\n)", name="conf", helpValue="fname")
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    @Option(desc="Read name contains this exact substring (e.g. flowcell/lane ID)", name="lane", helpValue="val1,val2,...")
    public void setNameSubstr1(String nameSubstr1) {
        this.nameSubstr1 = nameSubstr1.split(",");
    }

    @Option(desc="Read name contains this substring (e.g. barcode), allows for some mismatches", name="barcode", helpValue="val1,val2,...")
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

    @Option(desc="Calculate MD5 (will be written to {output}.md5)", name="md5")
    public void setCalcMD5(boolean calcMD5) {
        this.calcMD5 = calcMD5;
    }
    @Option(desc="Calculate SHA1 (will be written to {output}.sha1)", name="sha1")
    public void setCalcSHA1(boolean calcSHA1) {
        this.calcSHA1 = calcSHA1;
    }
    
    @Option(desc="Compress output files (gzip)", charName="z", name="gzip")
    public void setCompress(boolean compress) {
        this.compress = compress;
    }
    
    @Option(desc="Allow wildcards in barcodes (allow 'N' in barcode)", name="wildcard")
    public void setAllowWildcard(boolean allowWildcards) {
        this.allowWildcards = allowWildcards;
    }
    
    @Option(desc="Force overwriting existing files", charName="f", name="force")
    public void setForce(boolean force) {
        this.force = force;
    }
    
    @Option(desc="Allow X mismatches in the barcode seq", name="mismatches", defaultValue="0")
    public void setMismatches(int mismatches) {
        this.mismatches = mismatches;
    }
    
    @Exec
    public void exec() throws IOException, CommandArgumentException, FilteringException, NoSuchAlgorithmException {
        if (configFile != null) {
            if (nameSubstr1 != null || nameSubstr2 != null || readGroups != null) {
                throw new CommandArgumentException("--rg-id, --lane, and --barcode values obtained from config file: " + configFile);
            }
            
            List<String> tmpRgID = new ArrayList<String>();
            List<String> tmpStr1 = new ArrayList<String>();
            List<String> tmpStr2 = new ArrayList<String>();
            
            StringLineReader reader = new StringLineReader(configFile);
            
            boolean hasBarcode = false;
            
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
                    if (cols.length > 3) {
                        tmpStr2.add(cols[2]);
                        hasBarcode = true;
                    } else {
                        tmpStr2.add("");
                    }
                }
            }
            
            reader.close();

            readGroups = tmpRgID.toArray(new String[]{});
            nameSubstr1 = tmpStr1.toArray(new String[]{});
            if (hasBarcode) {
                nameSubstr2 = tmpStr2.toArray(new String[]{});
            }
        }
        
        if (filename == null) {
            throw new CommandArgumentException("Missing input filename!");
        }

//        if (outputTemplate == null && unmatchedFname == null) {
//            throw new CommandArgumentException("Missing --out filename template or --unmatched filename (at least one is required)!");
//        }

        if (nameSubstr1 == null && nameSubstr2 == null) {
            throw new CommandArgumentException("You must specify at least one value to split reads (--lane, --barcode)!");
        }

        if (readGroups == null) {
        	if (nameSubstr1 != null) {
	            readGroups = new String[nameSubstr1.length];
	            for (int i=0; i<nameSubstr1.length; i++) {
	                readGroups[i] = ""+i;
	            }
            } else {
	            readGroups = new String[nameSubstr2.length];
	            for (int i=0; i<nameSubstr2.length; i++) {
	                readGroups[i] = ""+i;
	            }
            }
        }
        
        if (nameSubstr1 != null && nameSubstr1.length != readGroups.length) {
            throw new CommandArgumentException("--lane and --rg-id must be the same length!");
        }
        
        if (nameSubstr2 != null && nameSubstr2.length != readGroups.length) {
            throw new CommandArgumentException("--barcode and --rg-id must be the same length!");
        }
        
        if (nameSubstr1 != null && nameSubstr2 != null && nameSubstr1.length != nameSubstr2.length) {
            throw new CommandArgumentException("--lane and --barcode must be the same length (if set)!");
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

        MessageDigest[] md5 = null;
        MessageDigest[] sha1 = null;
        
        if (outputTemplate != null) {
            outs = new OutputStream[readGroups.length];
            if (calcMD5) {
            	md5 = new MessageDigest[readGroups.length];
            }
            if (calcSHA1) {
            	sha1 = new MessageDigest[readGroups.length];
            }
        
            for (int i=0; i<readGroups.length; i++) {
                String fname = outputTemplate.replace("%RGID", readGroups[i]);
                if (compress) {
                    outs[i] = new GZIPOutputStream(new FileOutputStream(fname));
                } else {
                    outs[i] = new FileOutputStream(fname);
                }
                // these can cascade, so we can calc both at the same time
                if (calcMD5) {
                	md5[i] = MessageDigest.getInstance("MD5");
                	outs[i] = new DigestOutputStream(outs[i], md5[i]);
                }
                if (calcSHA1) {
                	sha1[i] = MessageDigest.getInstance("SHA1");
                	outs[i] = new DigestOutputStream(outs[i], sha1[i]);
                }
            }
        } else if (readGroups.length == 1) {
            if (calcMD5 || calcSHA1) {
                throw new CommandArgumentException("You can't calculate MD5/SHA1 when writing to stdout.");
            }
            outs = new OutputStream[readGroups.length];
            if (compress) {
                outs[0] = new GZIPOutputStream(System.out);
            } else {
                outs[0] = System.out;
            }
        } else {
            throw new CommandArgumentException("You can only write single barcode/lane combination to stdout.");
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
            String fqLine = read.getName() + " " + read.getComment();
            boolean matched = false;
            
            for (int i=0; i<readGroups.length; i++) {
                boolean match1 = false;
                boolean match2 = false;

                if (nameSubstr1 != null) {
                    if (fqLine.contains(nameSubstr1[i])) {
                        match1 = true;
                    }
                } else {
                    match1 = true;
                }

                if (nameSubstr2 != null && !nameSubstr2[i].equals("")) {
                    if (matches(fqLine, nameSubstr2[i], mismatches, allowWildcards)) {
                        match2 = true;
                    }
                } else {
                    match2 = true;
                }
                
                if (match1 && match2) {
                    matched = true;
                    counts[i]++;
                    if (outs != null) {
                        read.write(outs[i]);
                    }
                }
            }
            
            if (!matched) {
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
        
        if (calcMD5 || calcSHA1) {
	        if (outputTemplate != null) {
	            for (int i=0; i<readGroups.length; i++) {
	                String fname = outputTemplate.replace("%RGID", readGroups[i]);
	                
	                if (calcMD5) {
	                	// write the filename as if it were in the same dir
	                	DigestCmd.writeDigest(md5[i].digest(), new File(fname).getName(), fname+".md5");
	                }
	                if (calcSHA1) {
	                	// write the filename as if it were in the same dir
	                	DigestCmd.writeDigest(sha1[i].digest(), new File(fname).getName(), fname+".sha1");
	                }
	            }
	        }
        }

        
        for (int i=0; i<readGroups.length; i++) {
            System.err.println("Group " + readGroups[i] + ": "+counts[i]+" reads");
        }
        System.err.println("Unmatched: " + unmatchedCount +" reads");
        
    }

    public static boolean matches(String fqLine, String barcode, int mismatches, boolean allowWildcards2) {
        if (fqLine.contains(barcode)) {
            return true;
        }

        List<String> permutations = generateMismatchPatterns(barcode, mismatches);
        
        for (String bait: permutations) {
            if (allowWildcards2) {
                bait = bait.replaceAll("N", "\\[ACGT\\]");
            }
            if (Pattern.matches(bait, fqLine)) {
                return true;
            }
        }
        return false;
    }
    
    public static List<String> generateMismatchPatterns(String s, int iter) {
        List<String> out = new ArrayList<String>();
        out.add(s);
        out = generateMismatchPatternsInner(out, iter);
        return out;
    }
    
    public static List<String> generateMismatchPatternsInner(List<String> buffer, int iter) {
        if (iter <= 0) {
            return buffer;
        }
        int max = buffer.size();
        for (int i=0; i<max; i++) {
            String s = buffer.get(i);
            for (int j=0; j<s.length(); j++) {
                if (s.charAt(j) != '.') {
                    String tmp = s.substring(0, j);
                    tmp += ".";
                    tmp += s.substring(j+1);
                    // not optimal, but this is typically a small set of strings, so not terrible.
                    if (!buffer.contains(tmp)) {
                        buffer.add(tmp);
                    }
                }
            }
        }
        return generateMismatchPatternsInner(buffer, --iter);
    }
}
