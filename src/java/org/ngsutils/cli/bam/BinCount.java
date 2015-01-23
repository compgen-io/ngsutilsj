package org.ngsutils.cli.bam;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;
import org.ngsutils.cli.bam.count.BinCounter;
import org.ngsutils.cli.bam.count.BinCounter.BinCounterExporter;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-bins")
@Command(name="bam-bins", desc="Quickly count the number of reads that fall into bins (bins assigned based on 5' end of the read)", cat="bam")
public class BinCount extends AbstractOutputCommand {
    
    private String filename=null;
    
    private int binSize = 0;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean stranded = false;
    private boolean showAll = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Count bins of size [value] (default: 50bp)", longName="bins", defaultValue="50")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(description = "Show counts for all bins, including zero count bins", longName="all")
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }
    
    @Option(description = "Count bins in a strand-specific manner", longName="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
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

    @Override
    public void exec() throws NGSUtilsException, IOException {
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

        
        final TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + filename);
        writer.write_line("## library-orientation: " + orient.toString());

        
        writer.write_line("## binsize: " + binSize);
        writer.write_line("## stranded: " + (stranded? "true": "false"));
        writer.write_line("## counts: number of reads per bin (bins assigned based on 5' end of the read)");

        writer.write("chrom", "start", "end");
        if (stranded) {
            writer.write("strand");
        }
        writer.write("count");
        
        writer.eol();

        BinCounter counter = new BinCounter(orient, binSize, stranded, showAll, new BinCounterExporter() {
            @Override
            public void writeBin(String ref, int start, int end, Strand strand, int count) {
                writer.write(ref, ""+start, ""+end, strand.toString(), ""+count);
                try {
                    writer.eol();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }});
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {

            @Override
            public String msg(SAMRecord current) {
                return current.getReadName();
            }});
        
        while (it.hasNext()) {
            SAMRecord read = it.next();

            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
            
            counter.addRead(read);
        }
        
        counter.flush();
        writer.close();
        reader.close();
    }
}
