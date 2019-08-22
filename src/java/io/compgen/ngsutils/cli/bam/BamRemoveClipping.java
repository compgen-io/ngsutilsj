package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.bam.support.InvalidReadException;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-removeclipping", desc="Removes clipped bases (soft) from BAM file reads", category="bam", experimental=true)
public class BamRemoveClipping extends AbstractCommand {
    private String inFilename = null;
    private String outFilename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean writeFlags = false;
    private boolean forceOverwrite = false;

    
    @UnnamedArg(name = "INFILE OUTFILE")
    public void setFilenames(String[] filename) throws CommandArgumentException {
        if (filename.length != 2) {
            throw new CommandArgumentException("You must specify an INFILE and and OUTFILE");
        }
        this.inFilename = filename[0];
        this.outFilename = filename[1];
    }

    @Option(desc = "Write clipped data to flags (5' clipped bases => ZA:i, 3' clipped bases => ZB:i, Percentage of clipped bases => ZC:f)", name="flags")
    public void setWriteFlags(boolean writeFlags) {
        this.writeFlags = writeFlags;
    }

    @Option(desc = "Force overwriting an existing output file", name="force", charName="f")
    public void setForce(boolean forceOverwrite) {
        this.forceOverwrite = forceOverwrite;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (inFilename == null || outFilename == null) {
            throw new CommandArgumentException("You must specify an INFILE and and OUTFILE");
        }
        
        if (new File(outFilename).exists() && !forceOverwrite) {
            throw new CommandArgumentException("Output file: " + outFilename+ " exists! Use -f to force overwriting it.");
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
        if (inFilename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(inFilename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }


        SAMFileHeader header = reader.getFileHeader().clone();
        SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-removeclipping", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter writer = null;

        if (outFilename.equals("-")) {
            writer = factory.makeBAMWriter(header, true, new BufferedOutputStream(System.out));
        } else {
            writer = factory.makeBAMWriter(header, true, new File(outFilename));
        }
        
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        while (it.hasNext()) {
            SAMRecord read = it.next();
            try {
                writer.addAlignment(ReadUtils.removeClipping(read, this.writeFlags));
            } catch (InvalidReadException e) {
                throw new IOException(e);
            }
        }
        reader.close();
        writer.close();
    }
}
