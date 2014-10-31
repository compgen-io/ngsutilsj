package org.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMProgramRecord;
import net.sf.samtools.SAMRecord;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.filter.BamFilter;
import org.ngsutils.bam.filter.BedExclude;
import org.ngsutils.bam.filter.BedInclude;
import org.ngsutils.bam.filter.FilterFlags;
import org.ngsutils.bam.filter.NullFilter;
import org.ngsutils.bam.filter.PairedFilter;
import org.ngsutils.bam.filter.RequiredFlags;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.support.cli.AbstractCommand;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

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

    @Option(description = "Force checking read pairing (simple - same chromosome, reversed orientation)", longName="paired")
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

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
    
    @Option(description = "Library is in FR orientation", longName="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(description = "Library is in RF orientation", longName="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(description = "Library is in unstranded orientation (default)", longName="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Option(description = "Only keep properly paired reads", longName="proper-pairs")
    public void setProperPairs(boolean val) {
        requiredFlags |= ReadUtils.PROPER_PAIR_FLAG; 
    }

    @Option(description = "Only keep mapped reads (both reads if paired)", longName="mapped")
    public void setMapped(boolean val) {
        filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
    }

    @Option(description = "Only keep unmapped reads", longName="unmapped")
    public void setUnmapped(boolean val) {
        requiredFlags |= ReadUtils.READ_UNMAPPED_FLAG; 
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
        
        SAMFileReader reader;
        FileChannel channel = null;
        String name = null;
        if (filenames.get(0).equals("-")) {
            reader = new SAMFileReader(System.in);
            channel = null;
            name = "<stdin>";
        } else {
            File f = new File(filenames.get(0));
            name = f.getName();
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
                    
            reader = new SAMFileReader(fis);
        }
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        BamFilter parent = new NullFilter(ProgressUtils.getIterator(name, reader.iterator(), new FileChannelStats(channel), new ProgressMessage<SAMRecord>(){
            @Override
            public String msg(SAMRecord current) {
                return current.getReadName();
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
            out = factory.makeBAMWriter(header, false, outfile);
        } else {
            out = factory.makeSAMWriter(header,  false,  outStream);
        }

        long i = 0;
        for (SAMRecord read: parent) {
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
                
            }
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
