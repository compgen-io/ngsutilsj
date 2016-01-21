package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.filter.BamFilter;
import io.compgen.ngsutils.bam.filter.BedExclude;
import io.compgen.ngsutils.bam.filter.BedInclude;
import io.compgen.ngsutils.bam.filter.FilterFlags;
import io.compgen.ngsutils.bam.filter.JunctionWhitelist;
import io.compgen.ngsutils.bam.filter.NullFilter;
import io.compgen.ngsutils.bam.filter.PairingSanityFilter;
import io.compgen.ngsutils.bam.filter.RefExclude;
import io.compgen.ngsutils.bam.filter.RefInclude;
import io.compgen.ngsutils.bam.filter.RequiredFlags;
import io.compgen.ngsutils.bam.filter.TagEq;
import io.compgen.ngsutils.bam.filter.TagEqStr;
import io.compgen.ngsutils.bam.filter.TagMax;
import io.compgen.ngsutils.bam.filter.TagMin;
import io.compgen.ngsutils.bam.filter.UniqueMapping;
import io.compgen.ngsutils.bam.filter.UniqueStart;
import io.compgen.ngsutils.bam.filter.Whitelist;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(name="bam-filter", desc="Filters out reads based upon various criteria", category="bam")
public class BamFilterCli extends AbstractCommand {
    
    private List<String> filenames=null;
    
    private String tmpDir = null;
    
    private String bedExclude = null;
    private boolean bedExcludeRequireOne = false;
    private boolean bedExcludeRequireBoth = false;
    private boolean bedExcludeOnlyWithin = false;
    private boolean bedExcludeReadStartPos = false;
    
    private String bedIncludeFile = null;
    private boolean bedIncludeRequireOne = false;
    private boolean bedIncludeRequireBoth = false;
    private boolean bedIncludeOnlyWithin = false;
    private boolean bedIncludeReadStartPos = false;

    private String junctionWhitelist = null;
    private String whitelist = null;
    private String failedFilename = null;
    private String excludeRefs = null;
    private String includeRefs = null;

    private Map<String, Integer> minTagValues = null;
    private Map<String, Integer> maxTagValues = null;
    private Map<String, Integer> eqTagValues = null;
    private Map<String, String> eqStrTagValues = null;
    
    private boolean pairRef = false;
    private boolean unique = false;
    private boolean uniqueStart = false;
    
    private boolean lenient = false;
    private boolean silent = false;

    private int filterFlags = 0;
    private int requiredFlags = 0;
    
    private Orientation orient = Orientation.UNSTRANDED;

    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilename(List<String> filenames) throws CommandArgumentException {
        if (filenames.size() != 2) {
            throw new CommandArgumentException("You must specify both an input and an output file.");
        }
        this.filenames = filenames;
    }

    @Option(desc="Write temporary files here", name="tmpdir", helpValue="dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc="Force sanity checking of read pairing (simple - same chromosome, reversed orientation)", name="sane-pairs")
    public void setPairRef(boolean val) {
        this.pairRef = val;
    }

    @Option(desc="Require junction-spanning reads to span one of these junctions", name="junction-whitelist", helpValue="fname")
    public void setJunctionWhitelist(String junctionWhitelist) {
        this.junctionWhitelist = junctionWhitelist;
    }
    @Option(desc="Remove reads mapping to these references (comma-delimited)", name="ref-exclude", helpValue="ref")
    public void excludeRefs(String excludeRefs) {
        this.excludeRefs = excludeRefs;
    }

    @Option(desc="Keep only reads mapping to these references (comma-delimited)", name="ref-include", helpValue="ref")
    public void includeRefs(String includeRefs) {
        this.includeRefs = includeRefs;
    }

    @Option(desc="Keep only read names from this whitelist", name="whitelist", helpValue="fname")
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }

    @Option(desc="Write failed reads to this file (BAM)", name="failed", helpValue="fname")
    public void setFailedFilename(String failedFilename) {
        this.failedFilename = failedFilename;
    }

    @Option(desc="Exclude reads within BED regions", name="bed-exclude", helpValue="fname")
    public void setBedExcludeFile(String bedExclude) {
        this.bedExclude = bedExclude;
    }

    @Option(desc="BED Exclude option: only-within", name="bed-excl-only-within")
    public void setBEDExcludeWithin(boolean val) {
        this.bedExcludeOnlyWithin = val;
    }

    @Option(desc="BED Exclude option: require-one", name="bed-excl-require-one")
    public void setBEDExcludeRequireOne(boolean val) {
        this.bedExcludeRequireOne = val;
    }
    @Option(desc="BED Exclude option: start-pos", name="bed-excl-start-pos")
    public void setBEDExcludeReadStartPos(boolean val) {
        this.bedExcludeReadStartPos = val;
    }

    @Option(desc="BED Exclude option: require-both", name="bed-excl-require-both")
    public void setBEDExcludeRequireBoth(boolean val) {
        this.bedExcludeRequireBoth = val;
    }

    @Option(desc="Include reads within BED regions", name="bed-include", helpValue="fname")
    public void setBedIncludeFile(String bedIncludeFile) {
        this.bedIncludeFile = bedIncludeFile;
    }

    @Option(desc="BED Include option: only-within", name="bed-incl-only-within")
    public void setBEDIncludeWithin(boolean val) {
        this.bedIncludeOnlyWithin = val;
    }

    @Option(desc="BED Include option: require-one", name="bed-incl-require-one")
    public void setBEDIncludeKeep(boolean val) {
        this.bedIncludeRequireOne = val;
    }

    @Option(desc="BED Include option: require-both", name="bed-incl-require-both")
    public void setBEDIncludeRemove(boolean val) {
        this.bedIncludeRequireBoth = val;
    }
    @Option(desc="BED Include option: start-pos", name="bed-incl-start-pos")
    public void setBEDIncludeReadStartPos(boolean val) {
        this.bedIncludeReadStartPos = val;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Library is in FR orientation (only used for BED filters)", name="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(desc="Library is in RF orientation (only used for BED filters)", name="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(desc="Library is in unstranded orientation (only used for BED filters, default)", name="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Option(desc="Only keep properly paired reads", name="proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
        }
    }

    @Option(desc="Only keep reads that have one unique mapping (NH==1|IH==1|MAPQ>0)", name="unique-mapping")
    public void setUniqueMapping(boolean val) {
        unique=val; 
        setMapped(true);
    }

    @Option(desc="Keep at most one read per position (strand-specific, only for single-read fragments)", name="unique-start")
    public void setUniqueStart(boolean val) {
        uniqueStart=val; 
        setMapped(true);
    }

    @Option(desc="Only keep mapped reads (both reads if paired)", name="mapped")
    public void setMapped(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
        }
    }

    @Option(desc="Only keep unmapped reads", name="unmapped")
    public void setUnmapped(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.READ_UNMAPPED_FLAG;
        }
    }

    @Option(desc="No secondary mappings", name="nosecondary")
    public void setNoSecondary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.SUPPLEMENTARY_ALIGNMENT_FLAG;
        }
    }

    @Option(desc="No PCR duplicates", name="nopcrdup")
    public void setNoPCRDuplicates(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.DUPLICATE_READ_FLAG;
        }
    }

    @Option(desc="No QC failures", name="noqcfail")
    public void setNoQCFail(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_FAILS_VENDOR_QUALITY_CHECK_FLAG;
        }
    }

    @Option(desc="Filtering flags", name="filter-flags", defaultValue="0")
    public void setFilterFlags(int flag) {
        filterFlags |= flag; 
    }

    @Option(desc="Required flags", name="required-flags", defaultValue="0")
    public void setRequiredFlags(int flag) {
        requiredFlags |= flag; 
    }

    @Option(desc="Minimum tag value (tag:val, ex: AS:100,MAPQ:1)", name="tag-min")
    public void setMinTagValue(String vals) {
        if (minTagValues == null) {
            minTagValues = new HashMap<String, Integer>();
        }
        for (String val:vals.split(",")) {
            String key = val.split(":")[0];
            Integer value = Integer.parseInt(val.split(":")[1]);
            
            minTagValues.put(key, value);
        }
    }

    @Option(desc="Maximum tag value (tag:val, ex: NH:0)", name="tag-max")
    public void setMaxTagValue(String vals) {
        if (maxTagValues == null) {
            maxTagValues = new HashMap<String, Integer>();
        }
        
        for (String val:vals.split(",")) {
            String key = val.split(":")[0];
            Integer value = Integer.parseInt(val.split(":")[1]);
            
            maxTagValues.put(key, value);
        }
    }

    @Option(desc="Tag value equals (tag:val, ex: NH:0, int)", name="tag-eq")
    public void setEqTagValue(String vals) {
        if (eqTagValues == null) {
            eqTagValues = new HashMap<String, Integer>();
        }
        
        for (String val:vals.split(",")) {
            String key = val.split(":")[0];
            Integer value = Integer.parseInt(val.split(":")[1]);
            
            eqTagValues.put(key, value);
        }
        
    }

    @Option(desc="Tag value equals (tag:val, ex: NH:0, string)", name="tag-eqstr")
    public void setEqStringTagValue(String vals) {
        if (eqStrTagValues == null) {
            eqStrTagValues = new HashMap<String, String>();
        }
        
        for (String val:vals.split(",")) {
            String[] kv = val.split(":");
            eqStrTagValues.put(kv[0], kv[1]);
        }
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (filenames == null || filenames.size()!=2) {
            throw new CommandArgumentException("You must specify an input BAM filename and an output BAM filename!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filenames.get(0).equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filenames.get(0));
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        SAMFileWriterFactory factory = new SAMFileWriterFactory();

        String outFilename = filenames.get(1);
        File outfile = null;
        OutputStream outStream = null;
        
        if (outFilename.equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(outFilename);
        }
        
        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile!=null) {
            factory.setTempDirectory(outfile.getParentFile());
        }

        SAMFileHeader header = reader.getFileHeader().clone();
        SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("bam-filter", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

        SAMFileWriter out;
        if (outfile != null) {
            out = factory.makeBAMWriter(header, true, outfile);
        } else {
            out = factory.makeSAMWriter(header,  true,  outStream);
        }

        SAMFileWriter failedWriter = null;
        if (failedFilename != null) {
            failedWriter = factory.makeBAMWriter(header, true, new File(failedFilename));
        }
        
        BamFilter parent;
        if (channel == null) {
            parent = new NullFilter(reader.iterator(), failedWriter);

        } else {
            parent = new NullFilter(ProgressUtils.getIterator(name, reader.iterator(), new FileChannelStats(channel), new ProgressMessage<SAMRecord>(){
                @Override
                public String msg(SAMRecord current) {
                    if (current != null) {
                        return current.getReadName();
                    }
                    return null;
                }}, new CloseableFinalizer<SAMRecord>()), failedWriter);            
        }
        
        if (filterFlags > 0) {
            parent = new FilterFlags(parent, false, filterFlags);
            if (verbose) {
                System.err.println("FilterFlags: "+filterFlags);
            }

        }
        if (requiredFlags > 0) {
            parent = new RequiredFlags(parent, false, requiredFlags);
            if (verbose) {
                System.err.println("RequiredFlags: "+requiredFlags);
            }
        }
        if (unique) {
            parent = new UniqueMapping(parent, false);
            if (verbose) {
                System.err.println("Unique-mapping");
            }
        }
        if (uniqueStart) {
            parent = new UniqueStart(parent, false);
            if (verbose) {
                System.err.println("Unique-start");
            }
        }
        if (pairRef) {
            parent = new PairingSanityFilter(parent, false);
            if (verbose) {
                System.err.println("Paired-Ref");
            }
        }
        if (bedIncludeFile!=null) {
            parent = new BedInclude(parent, false, bedIncludeFile, orient);
            ((BedInclude)parent).setOnlyWithin(bedIncludeOnlyWithin);
            ((BedInclude)parent).setRequireOnePair(bedIncludeRequireOne);
            ((BedInclude)parent).setRequireBothPairs(bedIncludeRequireBoth);
            ((BedInclude)parent).setReadStartPos(bedIncludeReadStartPos);
            if (verbose) {
                System.err.println("BEDInclude: "+bedIncludeFile);
            }
        }
        if (bedExclude!=null) {
            parent = new BedExclude(parent, false, bedExclude, orient);
            ((BedExclude)parent).setOnlyWithin(bedExcludeOnlyWithin);
            ((BedExclude)parent).setRequireOnePair(bedExcludeRequireOne);
            ((BedExclude)parent).setRequireBothPairs(bedExcludeRequireBoth);
            ((BedExclude)parent).setReadStartPos(bedExcludeReadStartPos);
            if (verbose) {
                System.err.println("BEDExclude: "+bedExclude);
            }
        }
        if (includeRefs!=null) {
            parent = new RefInclude(parent, false, includeRefs);
            if (verbose) {
                System.err.println("RefInclude: "+includeRefs);
            }
        }
        if (excludeRefs!=null) {
            parent = new RefExclude(parent, false, excludeRefs);
            if (verbose) {
                System.err.println("RefExclude: "+excludeRefs);
            }
        }
        if (junctionWhitelist != null) {
            parent = new JunctionWhitelist(parent, false, junctionWhitelist);
            if (verbose) {
                System.err.println("JuntionWhitelist: "+junctionWhitelist);
            }
        }

        if (whitelist != null) {
            parent = new Whitelist(parent, false, whitelist);
            if (verbose) {
                System.err.println("Whitelist: "+whitelist);
            }
        }

        if (minTagValues != null) {
            parent = new TagMin(parent, false, minTagValues);
            if (verbose) {
                String outval = "";
                for (String k:minTagValues.keySet()) {
                    if (!outval.equals("")) {
                        outval+=",";
                    }
                    outval += k+":"+minTagValues.get(k);
                }
                System.err.println("Tag min: "+outval);
            }
        }

        if (maxTagValues != null) {
            parent = new TagMax(parent, false, maxTagValues);
            if (verbose) {
                String outval = "";
                for (String k:maxTagValues.keySet()) {
                    if (!outval.equals("")) {
                        outval+=",";
                    }
                    outval += k+":"+maxTagValues.get(k);
                }
                System.err.println("Tag max: "+outval);
            }
        }

        if (eqTagValues != null) {
            parent = new TagEq(parent, false, eqTagValues);
            if (verbose) {
                String outval = "";
                for (String k:eqTagValues.keySet()) {
                    if (!outval.equals("")) {
                        outval+=",";
                    }
                    outval += k+":"+eqTagValues.get(k);
                }
                System.err.println("Tag eq (int): "+outval);
            }
        }

        if (eqStrTagValues != null) {
            parent = new TagEqStr(parent, false, eqStrTagValues);
            if (verbose) {
                String outval = "";
                for (String k:eqStrTagValues.keySet()) {
                    if (!outval.equals("")) {
                        outval+=",";
                    }
                    outval += k+":"+eqStrTagValues.get(k);
                }
                System.err.println("Tag eq (string): "+outval);
            }
        }

        for (SAMRecord read: parent) {
            if (read != null) {
                out.addAlignment(read);
            }
        }

        if (verbose) {
            dumpStats(parent);
        }
        reader.close();
        out.close();
        if (failedWriter != null) {
            failedWriter.close();
        }
    }
    
    private void dumpStats(BamFilter filter) {
        if (filter.getParent()!=null) {
            dumpStats(filter.getParent());
        }
        System.err.println(filter.getClass().getSimpleName());
        System.err.println("    total: "+filter.getTotal());
        System.err.println("  removed: "+filter.getRemoved());
    }
}
