package io.compgen.ngsutils.cli.bam;

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
import io.compgen.common.Counter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.bam.FindDuplicateReads;
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
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

@Command(name="bam-dups", desc="Flags or removes duplicate reads", category="bam", 
         doc="Flags reads as duplicates based upon their paired left-most and right-most positions. "
           + "For inter-chromosomal reads, it is based on the start positions of the reads. This "
           + "requires an accurate TLEN/ISIZE field.")
public class BamFlagDuplicates extends AbstractCommand {
    private List<String> filenames = null;
    private boolean lenient = false;
    private boolean silent = false;

    private boolean remove = false;
    private String failedFilename = null;
    private String tmpDir = null;
    private String tagName = null;
    private String tagValue = null;
    private boolean scoreMapQ = false;
    
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

    @Option(desc = "Remove duplicate reads and save them to this file (implies --rm)", name="rmfile")
    public void setRemovedFile(String failedFilename) {
        this.failedFilename = failedFilename;
        this.remove = true;
    }

    @Option(desc = "Attribute tag to add to duplicates (format: XX:Z:Value)", name="tag")
    public void setTagAttribute(String tagAttribute) throws CommandArgumentException {
        if (tagAttribute != null) {
            String[] vals = tagAttribute.split(":");
            if (vals.length != 3) {
                throw new CommandArgumentException("Invalid argument: --tag " + tagAttribute);
            }

            tagName = vals[0]+":"+vals[1];
            tagValue = vals[2];

        }
    }

    @Option(desc = "Remove duplicate reads (default is to flag them only)", name="rm")
    public void setRemoveDups(boolean remove) {
        this.remove = remove;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use MAPQ/AS scores to determine best read (default: sum-of-quals)", name="score-mapq")
    public void setScoreMethodMapQAS(boolean val) {
        if (val) {
            this.scoreMapQ  = true;
        }
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filenames == null || filenames.size() != 2) {
            throw new CommandArgumentException("You must specify input and output BAM filename!");
        }
        
        if (failedFilename != null) {
            remove = true;
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

        if (reader.getFileHeader().getSortOrder() != SortOrder.coordinate) {
            throw new CommandArgumentException("Input BAM file must be sorted by coordinate!");
        }
        
        
        SAMFileHeader header = reader.getFileHeader().clone();
        SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-dups", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

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

        SAMFileWriter out;
        if (outfile != null) {
            out = factory.makeBAMWriter(header, true, outfile);
        } else {
            out = factory.makeBAMWriter(header,  true,  outStream);
        }

        SAMFileWriter failedWriter = null;
        if (failedFilename != null) {
            failedWriter = factory.makeBAMWriter(header, true, new File(failedFilename));
        }

        final FindDuplicateReads dups = new FindDuplicateReads(out, remove, failedWriter, tagName, tagValue);
        if (scoreMapQ) {
            dups.setScoringMethodMapQ();
        }
        final Counter counter = new Counter();
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            @Override
            public String msg(SAMRecord current) {
                return counter.getValue()+" "+current.getReferenceName()+":"+current.getAlignmentStart() + " " + dups.getDuplicateSites()+","+dups.getDuplicateReads();
            }}, new CloseableFinalizer<SAMRecord>());
        
        while (it.hasNext()) {
            counter.incr();
            dups.addRead(it.next());
        }
        dups.close();
        reader.close();
        out.close();

        System.err.println("Total-reads:\t" + counter.getValue());
        System.err.println("Duplicate-sites:\t" + dups.getDuplicateSites());
        System.err.println("Duplicate-reads:\t" + dups.getDuplicateReads());
        System.err.println("Unmapped-reads (skipped):\t" + dups.getUnmappedReads());
        
        if (failedWriter != null) {
            failedWriter.close();
        }
    }
}
