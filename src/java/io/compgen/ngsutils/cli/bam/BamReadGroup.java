package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMReadGroupRecord;
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
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Command(name="bam-readgroup", desc="Add a read group ID to each read in a BAM file", category="bam")
public class BamReadGroup extends AbstractCommand {
    private String[] filenames = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean quiet = false;

    private String readGroupID = null;
    private String readGroupCenter = null;
    private String readGroupDesc = null;
    private String readGroupDate = null;
    private String readGroupPlatform = null;
    private String readGroupPlatformUnit = null;
    private String readGroupLibrary = null;
    private String readGroupSample = null;
    
    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilename(String[] filenames) throws CommandArgumentException {
        if (filenames.length != 2) {
            throw new CommandArgumentException("You must specify both an input and an output file.");
        }
        this.filenames = filenames;
    }

    @Option(desc = "Read Group ID (ID)", name="rg-id")
    public void setReadGroupID(String readGroupID) {
        this.readGroupID = readGroupID;
    }
    
    @Option(desc = "Read Group Center (CN)", name="rg-center")
    public void setReadGroupCenter(String readGroupCenter) {
        this.readGroupCenter = readGroupCenter;
    }
    
    @Option(desc = "Read Group Date (DT)", name="rg-date")
    public void setReadGroupDate(String readGroupDate) {
        this.readGroupDate = readGroupDate;
    }
    
    @Option(desc = "Read Group Description (DS)", name="rg-desc")
    public void setReadGroupDesc(String readGroupDesc) {
        this.readGroupDesc = readGroupDesc;
    }
    
    @Option(desc = "Read Group Library (LB)", name="rg-library")
    public void setReadGroupLibrary(String readGroupLibrary) {
        this.readGroupLibrary = readGroupLibrary;
    }
    
    @Option(desc = "Read Group Platform (PL)", name="rg-platform")
    public void setReadGroupPlatform(String readGroupPlatform) {
        this.readGroupPlatform = readGroupPlatform;
    }
    
    @Option(desc = "Read Group Platform Unit (PU)", name="rg-unit")
    public void setReadGroupPlatformUnit(String readGroupPlatformUnit) {
        this.readGroupPlatformUnit = readGroupPlatformUnit;
    }

    @Option(desc = "Read Group Sample (SM)", name="rg-sample")
    public void setReadGroupSample(String readGroupSample) {
        this.readGroupSample = readGroupSample;
    }
    
    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(desc = "Don't display progress", name="quiet")
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filenames == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
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
        if (filenames[0].equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filenames[0]);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        SAMFileHeader header = reader.getFileHeader().clone();
        SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("bam-readgroup", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

        SAMReadGroupRecord rg = new SAMReadGroupRecord(readGroupID);
        if (readGroupCenter != null) {
            rg.setSequencingCenter(readGroupCenter);
        }
        
        if (readGroupDesc != null) {
            rg.setDescription(readGroupDesc);
        }
        
        if (readGroupDate != null) {
            rg.setAttribute("DT", readGroupDate);
        }
        
        if (readGroupPlatform != null) {
            rg.setPlatform(readGroupPlatform);
        }

        if (readGroupPlatformUnit != null) {
            rg.setPlatformUnit(readGroupPlatformUnit);
        }

        if (readGroupLibrary != null) {
            rg.setLibrary(readGroupLibrary);
        }
        
        if (readGroupSample != null) {
            rg.setSample(readGroupSample);
        }
        
        header.addReadGroup(rg);
        
        String outFilename = filenames[1];
        File outfile = null;
        OutputStream outStream = null;
        
        if (outFilename.equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(outFilename);
        }
        
        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter out;
        if (outfile != null) {
            out = factory.makeBAMWriter(header, true, outfile);
        } else {
            out = factory.makeSAMWriter(header,  true,  outStream);
        }
        
        Iterator<SAMRecord> it;
        
        if (!quiet){
            it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        } else {
            it = reader.iterator();
        }
        
        while (it.hasNext()) {
            SAMRecord read = it.next();
            read.setAttribute("RG", readGroupID);
            out.addAlignment(read);
        }
        reader.close();
        out.close();
    }
}
