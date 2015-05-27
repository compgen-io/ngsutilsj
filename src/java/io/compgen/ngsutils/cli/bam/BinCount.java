package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.cli.bam.count.BinCounter;
import io.compgen.ngsutils.cli.bam.count.BinCounter.BinCounterExporter;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

@Command(name="bam-bins", desc="Quickly count the number of reads that fall into bins (bins assigned based on 5' end of the first read)", category="bam")
public class BinCount extends AbstractOutputCommand {
    
    private String filename=null;
    
    private int binSize = 0;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean stranded = false;
    private boolean showAll = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Count bins of size [value] (default: 50bp)", name="bins", defaultValue="50")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(desc="Show counts for all bins, including zero count bins", name="all")
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }
    
    @Option(desc="Count bins in a strand-specific manner", name="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
    }
    
    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(desc="Library is in FR orientation", name="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(desc="Library is in RF orientation", name="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(desc="Library is in unstranded orientation (default)", name="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Exec
    public void exec() throws IOException {
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
            }}, new CloseableFinalizer<SAMRecord>());
        
        while (it.hasNext()) {
            SAMRecord read = it.next();

            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
            
            if (read.getReadPairedFlag() && !read.getFirstOfPairFlag()) {
                // only count the first of the pair...
                continue;
            }
            
            counter.addRead(read);
        }
        
        counter.flush();
        writer.close();
        reader.close();
    }
}
