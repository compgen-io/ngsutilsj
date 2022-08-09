package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;

@Command(name = "bam-expressed", desc = "For a BAM file, output all regions with significant coverage in BED format.", category = "bam", doc = "This function will find areas of expression in RNA or coverage in \n"
        + "targetted DNA sequencing. Contiguous regions with sufficient coverage \n"
        + "are outputted in BED format. This BED file can then be used as a \n"
        + "filter for other analysis to make sure you are comparing data from "
        + "regions of similar coverage/expression.")
public class BamExpressedRegions extends AbstractOutputCommand {

    // private String pileupFilename = null;
    private String bamFilename = null;

    private int windowExtend = 0;
    private int minSize = 50;
    private int minDepth = 1;
    private int minBaseQual = 13;
    private int minMapQ = 0;

    private boolean properPairs = false;

    @Option(desc = "Only count properly-paired reads", name = "paired")
    public void setProperPairs(boolean properPairs) {
        this.properPairs = properPairs;
    }

    @Option(desc = "Minimum depth to output (set to 0 to output all bases)", name = "min-depth", defaultValue = "1")
    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    @Option(desc = "Minimum size of a region", name = "min-size", defaultValue = "50")
    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    @Option(desc = "Extend regions X bases to find adjacent regions", name = "extend", defaultValue = "0")
    public void setWindowExtend(int windowExtend) {
        this.windowExtend = windowExtend;
    }

    @Option(desc = "Minimum base quality", name = "min-basequal", defaultValue = "13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(desc = "Minimum read mapping quality (MAPQ)", name = "min-mapq", defaultValue = "0")
    public void setMinMapQual(int minMapQ) {
        this.minMapQ = minMapQ;
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.bamFilename = filename;
    }

    public BamExpressedRegions() {
    }

    @Exec
    public void exec() throws Exception {
        if (bamFilename == null) {
            throw new CommandArgumentException("You must specify a BAM file for input.");
        }

        TabWriter writer = new TabWriter(out);

        BAMPileup pileup = new BAMPileup(bamFilename);
        pileup.setDisableBAQ(true);
        pileup.setExtendedBAQ(false);
        pileup.setFlagRequired(properPairs ? 0x2 : 0);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMinMappingQual(minMapQ);
        pileup.setVerbose(verbose);

        String curRef=null;
        int curStart = -1;
        int curEnd = -1;
        int peak = -1;
        int regionCount = 0;
        int progress = 0;
        
        CloseableIterator<PileupRecord> it = pileup.pileup();
        for (PileupRecord record : IterUtils.wrap(it)) {
            if (curRef == null || !record.ref.equals(curRef)) {
                if (curRef != null && curEnd - curStart > minSize) {
                    writer.write(curRef, ""+curStart, ""+curEnd, "region_"+(++regionCount),""+peak);
                    writer.eol();
                }
                if (verbose) {
                    System.err.println(record.ref);
                }
                curRef = record.ref;
                curStart = -1;
                curEnd = -1;
                peak = -1;
                progress = 100000;
            }

            if (verbose && record.pos > progress) {
                System.err.println(record.ref+":"+progress);
                progress += 100000;
            }
            
            if (record.getSampleRecords(0).coverage < minDepth) {
                continue;
            }

            if (curStart == -1 || curEnd + 1 + windowExtend < record.pos) {
                if (curStart > -1 && curEnd - curStart > minSize) {
                    writer.write(curRef, ""+curStart, ""+curEnd, "region_"+(++regionCount),""+peak);
                    writer.eol();
                }

                curStart = record.pos;
                peak = record.getSampleRecords(0).coverage;
            }

            if (record.getSampleRecords(0).coverage > peak) {
                peak = record.getSampleRecords(0).coverage;
            }
            
            curEnd = record.pos;
        }
        if (curRef != null && curStart > -1) {
            if (curEnd - curStart > minSize) {
                writer.write(curRef, ""+curStart, ""+curEnd, "region_"+(++regionCount),""+peak);
                writer.eol();
            }
        }

        it.close();
        writer.close();
    }

}
