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
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.NGSUtilsException;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.filter.BamFilter;
import io.compgen.ngsutils.bam.filter.BedExclude;
import io.compgen.ngsutils.bam.filter.BedInclude;
import io.compgen.ngsutils.bam.filter.FilterFlags;
import io.compgen.ngsutils.bam.filter.NullFilter;
import io.compgen.ngsutils.bam.filter.PairedFilter;
import io.compgen.ngsutils.bam.filter.RequiredFlags;
import io.compgen.ngsutils.bam.filter.UniqueMapping;
import io.compgen.ngsutils.bam.filter.UniqueStart;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.cli.AbstractCommand;
import io.compgen.ngsutils.support.cli.Command;
import io.compgen.ngsutils.support.progress.FileChannelStats;
import io.compgen.ngsutils.support.progress.ProgressMessage;
import io.compgen.ngsutils.support.progress.ProgressUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-filter")
@Command(name="bam-filter", desc="Filters out reads based upon various criteria", cat="bam")
public class BamFilterCli extends AbstractCommand {
    
    private List<String> filenames=null;
    
    private String tmpDir = null;
    
    private String bedExclude = null;
    private boolean bedExcludeRequireOne = false;
    private boolean bedExcludeRequireBoth = false;
    private boolean bedExcludeOnlyWithin = false;
    
    private String bedInclude = null;
    private boolean bedIncludeRequireOne = false;
    private boolean bedIncludeRequireBoth = false;
    private boolean bedIncludeOnlyWithin = false;

    private boolean paired = false;
    private boolean unique = false;
    private boolean uniqueStart = false;
    
    private boolean lenient = false;
    private boolean silent = false;

    private int filterFlags = 0;
    private int requiredFlags = 0;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "INFILE OUTFILE")
    public void setFilename(List<String> filenames) {
        this.filenames = filenames;
    }

    @Option(description = "Write temporary files here", longName="tmpdir", defaultToNull=true)
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(description = "Force sanity checking of read pairing (simple - same chromosome, reversed orientation)", longName="paired")
    public void setPaired(boolean val) {
        this.paired = val;
    }

    @Option(description = "Exclude reads within BED regions", longName="bed-exclude", defaultToNull=true)
    public void setBedExcludeFile(String bedExclude) {
        this.bedExclude = bedExclude;
    }

    @Option(description = "BED Exclude option: only-within", longName="bed-excl-only-within")
    public void setBEDExcludeWithin(boolean val) {
        this.bedExcludeOnlyWithin = val;
    }

    @Option(description = "BED Exclude option: require-one", longName="bed-excl-require-one")
    public void setBEDExcludeRequireOne(boolean val) {
        this.bedExcludeRequireOne = val;
    }

    @Option(description = "BED Exclude option: require-both", longName="bed-excl-require-both")
    public void setBEDExcludeRequireBoth(boolean val) {
        this.bedExcludeRequireBoth = val;
    }
    @Option(description = "Include reads within BED regions", longName="bed-include", defaultToNull=true)
    public void setBedIncludeFile(String bedInclude) {
        this.bedInclude = bedInclude;
    }

    @Option(description = "BED Include option: only-within", longName="bed-incl-only-within")
    public void setBEDIncludeWithin(boolean val) {
        this.bedIncludeOnlyWithin = val;
    }

    @Option(description = "BED Include option: require-one", longName="bed-incl-require-one")
    public void setBEDIncludeKeep(boolean val) {
        this.bedIncludeRequireOne = val;
    }

    @Option(description = "BED Include option: require-both", longName="bed-incl-require-both")
    public void setBEDIncludeRemove(boolean val) {
        this.bedIncludeRequireBoth = val;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Library is in FR orientation (only used for BED filters)", longName="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(description = "Library is in RF orientation (only used for BED filters)", longName="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(description = "Library is in unstranded orientation (only used for BED filters, default)", longName="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Option(description = "Only keep properly paired reads", longName="proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
        }
    }

    @Option(description = "Only keep reads that have one unique mapping", longName="unique-mapping")
    public void setUniqueMapping(boolean val) {
        unique=val; 
        setMapped(true);
    }

    @Option(description = "Keep at most one read per position (strand-specific, only for single-read fragments)", longName="unique-start")
    public void setUniqueStart(boolean val) {
        uniqueStart=val; 
        setMapped(true);
    }

    @Option(description = "Only keep mapped reads (both reads if paired)", longName="mapped")
    public void setMapped(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
        }
    }

    @Option(description = "Only keep unmapped reads", longName="unmapped")
    public void setUnmapped(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.READ_UNMAPPED_FLAG;
        }
    }

    @Option(description = "No secondary mappings", longName="nosecondary")
    public void setNoSecondary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.SUPPLEMENTARY_ALIGNMENT_FLAG;
        }
    }

    @Option(description = "No PCR duplicates", longName="nopcrdup")
    public void setNoPCRDuplicates(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.DUPLICATE_READ_FLAG;
        }
    }

    @Option(description = "No QC failures", longName="noqcfail")
    public void setNoQCFail(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_FAILS_VENDOR_QUALITY_CHECK_FLAG;
        }
    }

    @Option(description = "Filtering flags", longName="filter-flags", defaultValue="0")
    public void setFilterFlags(int flag) {
        filterFlags |= flag; 
    }

    @Option(description = "Required flags", longName="required-flags", defaultValue="0")
    public void setRequiredFlags(int flag) {
        requiredFlags |= flag; 
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filenames == null || filenames.size()!=2) {
            throw new ArgumentValidationException("You must specify an input BAM filename and an output BAM filename!");
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

        
        BamFilter parent = new NullFilter(ProgressUtils.getIterator(name, reader.iterator(), new FileChannelStats(channel), new ProgressMessage<SAMRecord>(){
            @Override
            public String msg(SAMRecord current) {
                if (current != null) {
                    return current.getReadName();
                }
                return null;
            }}));
        
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
        if (paired) {
            parent = new PairedFilter(parent, false);
            if (verbose) {
                System.err.println("Paired");
            }
        }
        if (bedInclude!=null) {
            parent = new BedInclude(parent, false, bedInclude, orient);
            ((BedInclude)parent).setOnlyWithin(bedIncludeOnlyWithin);
            ((BedInclude)parent).setRequireOnePair(bedIncludeRequireOne);
            ((BedInclude)parent).setRequireBothPairs(bedIncludeRequireBoth);
            if (verbose) {
                System.err.println("BEDInclude: "+bedInclude);
            }
        }
        if (bedExclude!=null) {
            parent = new BedExclude(parent, false, bedExclude, orient);
            ((BedExclude)parent).setOnlyWithin(bedExcludeOnlyWithin);
            ((BedExclude)parent).setRequireOnePair(bedExcludeRequireOne);
            ((BedExclude)parent).setRequireBothPairs(bedExcludeRequireBoth);
            if (verbose) {
                System.err.println("BEDExclude: "+bedExclude);
            }
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
