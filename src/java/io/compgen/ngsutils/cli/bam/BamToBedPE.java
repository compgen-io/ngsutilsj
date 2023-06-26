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

@Command(name="bam-tobedpe", desc="Writes read positions to a BEDPE file", category="bam", experimental=true, 
doc="The mapped position of a read is writen to a 6 column BED file.\n" 
  + "If the file is paired end, then by default only the first read\n"
  + "of the pair will be written to the file (this is configurable).")

public class BamToBedPE extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;

    private String includeList = null;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
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

        if (filename.equals("-")) {
            throw new CommandArgumentException("You must specify an input BAM file, not stdin!");
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
        SamReader reader2 = null; // for pulling the mate pair

        String name;
        FileChannel channel = null;
        File f = new File(filename);
        FileInputStream fis = new FileInputStream(f);
        channel = fis.getChannel();
        reader = readerFactory.open(SamInputResource.of(fis));
        name = f.getName();

        // open the second reader with the same file
        reader2 = readerFactory.open(SamInputResource.of(f));
        if (!reader2.hasIndex()) {
            throw new CommandArgumentException("You must specify an indexed BAM file!");
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
            if (read.getReadUnmappedFlag() || read.getMateUnmappedFlag()) {
                continue;
            }
            
            if (read.isSecondaryOrSupplementary()) {
            	continue;
            }
            
        	if (!read.getFirstOfPairFlag()) {
        		// only process first reads... we'll get the seconds as part of that.
                continue;
        	}
            
            if (includeReadNames != null && !includeReadNames.contains(read.getReadName())) {
                continue;
            }
            
            // Note: this can fail for certain BAM files with supplemental reads
            //       apparently this is addressed in a future version of the library
            
            SAMRecord pair = reader2.queryMate(read);
            
            if (pair == null) {
            	continue;
            }
            
            writer.write(read.getReferenceName());
            writer.write(read.getAlignmentStart());
            writer.write(read.getAlignmentEnd());
            writer.write(pair.getReferenceName());
            writer.write(pair.getAlignmentStart());
            writer.write(pair.getAlignmentEnd());
            writer.write(read.getReadName());
            writer.write(read.getMappingQuality());
            writer.write(read.getReadNegativeStrandFlag() ? "-" : "+");
            writer.write(pair.getReadNegativeStrandFlag() ? "-" : "+");
            writer.eol();
            i++;
            
        }
        writer.close();
        reader.close();
        System.err.println("Successfully read: "+i+" records.");
    }
}
