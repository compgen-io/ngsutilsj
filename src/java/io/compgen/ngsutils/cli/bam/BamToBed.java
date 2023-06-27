package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-tobed", desc="Writes read positions to a BED6 file", category="bam", experimental=true, 
doc="The mapped position of a read is writen to a 6 column BED file.\n" 
  + "If the file is paired end, then by default only the first read\n"
  + "of the pair will be written to the file (this is configurable).")

public class BamToBed extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean second = false;

    private String includeList = null;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Output second read (R2, default is to only output R1 if paired)", name="second-read")
    public void setSecond(boolean second) {
        this.second = second;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(desc="Keep only read names from this include list", name="include", helpValue="fname")
    public void setIncludeList(String includeList) {
        this.includeList = includeList;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }

        Set<String> includeReadNames = null;
        if (includeList != null) {
            includeReadNames = new HashSet<String>();
            for (String s: new StringLineReader(includeList)) {
                includeReadNames.add(StringUtils.strip(s));
            }
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
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        TabWriter writer = new TabWriter(out);
//        writer.write_line("## program: " + NGSUtils.getVersion());
//        writer.write_line("## cmd: " + NGSUtils.getArgs());


        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        long i = 0;
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.getReadUnmappedFlag()) {
                continue;
            }
            
            if (read.isSecondaryOrSupplementary()) {
            	continue;
            }
            
            if (read.getReadPairedFlag()) {
            	if (second && read.getFirstOfPairFlag()) {
                    continue;
            	}
            	if (!second && !read.getFirstOfPairFlag()) {
                    continue;
            	}
            }
            
            if (includeReadNames != null && !includeReadNames.contains(read.getReadName())) {
                continue;
            }
            
            writer.write(read.getReferenceName());
            writer.write(read.getAlignmentStart());
            writer.write(read.getAlignmentEnd());
            writer.write(read.getReadName());
            writer.write(read.getMappingQuality());
            writer.write(read.getReadNegativeStrandFlag() ? "-" : "+");
            writer.eol();
            i++;
            
        }
        writer.close();
        reader.close();
        System.err.println("Successfully read: "+i+" records.");
    }
}
