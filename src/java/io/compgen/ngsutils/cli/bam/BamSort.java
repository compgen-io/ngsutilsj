package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
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
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-sort", desc="Sort a BAM file using HTSJDK ordering", category="bam")
public class BamSort extends AbstractCommand {
    private String[] filenames = null;
    private String tmpDir = null;

    private boolean byName = false;
    
    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilename(String[] filenames) {
        this.filenames = filenames;
    }

    @Option(desc="Write temporary files here", name="tmpdir", helpValue="dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc = "Sort by query/read name (default: by position)", name="by-name")
    public void SetByName(boolean byName) {
        this.byName = byName;
    }




    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filenames == null || filenames.length != 2) {
            throw new CommandArgumentException("You must specify input and output filename!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        readerFactory.validationStringency(ValidationStringency.SILENT);

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
        
        SAMFileWriter writer = null;

        SAMFileWriterFactory factory = new SAMFileWriterFactory();

        File outfile = null;
        OutputStream outStream = null;
        
        if (filenames[1].equals("-")) {
            outStream = new BufferedOutputStream(System.out);
        } else {
            outfile = new File(filenames[1]);
        }
        
        if (tmpDir != null) {
            factory.setTempDirectory(new File(tmpDir));
        } else if (outfile == null || outfile.getParent() == null) {
            factory.setTempDirectory(new File(".").getCanonicalFile());
        } else if (outfile!=null) {
            factory.setTempDirectory(outfile.getParentFile());
        }

        SAMFileHeader header = reader.getFileHeader().clone();
        
        if (byName) {
        	header.setSortOrder(SortOrder.queryname);
        } else {
        	header.setSortOrder(SortOrder.coordinate);
        }
        
        SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-sort", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);


        if (outfile != null) {
            writer = factory.makeBAMWriter(header, false, outfile);
        } else {
            writer = factory.makeBAMWriter(header,  false,  outStream);
        }
 
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        long totalCount = 0;
        
        while (it.hasNext()) {
            totalCount++;
            SAMRecord read = it.next();
            writer.addAlignment(read);
        }

        reader.close();
        writer.close();
        System.err.println("Total reads     : "+totalCount);
    }
}
