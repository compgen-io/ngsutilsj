package io.compgen.ngsutils;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMProgramRecord;
import io.compgen.cmdline.Help;
import io.compgen.cmdline.License;
import io.compgen.cmdline.MainBuilder;
import io.compgen.common.StringUtils;
import io.compgen.common.progress.SocketProgress;
import io.compgen.ngsutils.cli.annotate.GTFAnnotate;
import io.compgen.ngsutils.cli.annotate.RepeatAnnotate;
import io.compgen.ngsutils.cli.bam.BamCheck;
import io.compgen.ngsutils.cli.bam.BamCount;
import io.compgen.ngsutils.cli.bam.BamCoverage;
import io.compgen.ngsutils.cli.bam.BamFilterCli;
import io.compgen.ngsutils.cli.bam.BamReadGroup;
import io.compgen.ngsutils.cli.bam.BamSampleReads;
import io.compgen.ngsutils.cli.bam.BamStats;
import io.compgen.ngsutils.cli.bam.BamToBed;
import io.compgen.ngsutils.cli.bam.BamToFastq;
import io.compgen.ngsutils.cli.bam.BinCount;
import io.compgen.ngsutils.cli.bam.PileupCli;
import io.compgen.ngsutils.cli.bed.BedCleanScore;
import io.compgen.ngsutils.cli.bed.BedCount;
import io.compgen.ngsutils.cli.bed.BedNearest;
import io.compgen.ngsutils.cli.bed.BedReduce;
import io.compgen.ngsutils.cli.bed.BedResize;
import io.compgen.ngsutils.cli.bed.BedToBed3;
import io.compgen.ngsutils.cli.bed.BedToBed6;
import io.compgen.ngsutils.cli.bed.BedToFasta;
import io.compgen.ngsutils.cli.fasta.FastaFilter;
import io.compgen.ngsutils.cli.fasta.FastaSubseq;
import io.compgen.ngsutils.cli.fasta.FastaTag;
import io.compgen.ngsutils.cli.fasta.FastaWrap;
import io.compgen.ngsutils.cli.fastq.FastqCheck;
import io.compgen.ngsutils.cli.fastq.FastqFilterCli;
import io.compgen.ngsutils.cli.fastq.FastqMerge;
import io.compgen.ngsutils.cli.fastq.FastqSeparate;
import io.compgen.ngsutils.cli.fastq.FastqSort;
import io.compgen.ngsutils.cli.fastq.FastqSplit;
import io.compgen.ngsutils.cli.fastq.FastqToBam;
import io.compgen.ngsutils.cli.gtf.GTFExport;
import io.compgen.ngsutils.support.stats.FisherCli;

import java.io.IOException;

@SuppressWarnings("deprecation")
public class NGSUtils {
    
    public static void main(String[] args) {
        NGSUtils.args = StringUtils.join(" ", args);
        
        SocketProgress.setHeader("NGSUtilsJ - " + NGSUtils.args);
        try {
            new MainBuilder()
                .setProgName("ngsutilsj")
                .setHelpHeader("NGSUtilsJ - Data wrangling for NGS\n---------------------------------------")
                .setDefaultUsage("Usage: ngsutilsj cmd [options]")
                .setHelpFooter("http://compgen.io/ngsutilsj\n" + getVersion())
                .setCategoryOrder(new String[] { "bam", "bed", "fasta", "fastq", "gtf", "annotation", "help"})
                .addCommand(License.class)
                .addCommand(Help.class)
                .addCommand(FastqToBam.class)
                .addCommand(FastqSort.class)
                .addCommand(FastqMerge.class)
                .addCommand(FastqCheck.class)
                .addCommand(FastqSeparate.class)
                .addCommand(FastqSplit.class)
                .addCommand(FastqFilterCli.class)
                .addCommand(BinCount.class)
                .addCommand(BamCheck.class)
                .addCommand(BamCount.class)
                .addCommand(BamSampleReads.class)
                .addCommand(BamCoverage.class)
                .addCommand(BamToFastq.class)
                .addCommand(BamFilterCli.class)
                .addCommand(BamReadGroup.class)
                .addCommand(BamStats.class)
                .addCommand(BamToBed.class)
                .addCommand(FastaSubseq.class)
                .addCommand(PileupCli.class)
                .addCommand(RepeatAnnotate.class)
                .addCommand(GTFAnnotate.class)
                .addCommand(GTFExport.class)
                .addCommand(FastaTag.class)
                .addCommand(FastaFilter.class)
                .addCommand(FastaWrap.class)
                .addCommand(BedResize.class)
                .addCommand(BedReduce.class)
                .addCommand(BedCount.class)
                .addCommand(BedNearest.class)
                .addCommand(BedToFasta.class)
                .addCommand(BedToBed3.class)
                .addCommand(BedToBed6.class)
                .addCommand(BedCleanScore.class)
                .addCommand(FisherCli.class)
                .findAndRun(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

	public static String getVersion() {
	    try {
            return MainBuilder.readFile("io/compgen/ngsutilsj/VERSION");
        } catch (IOException e) {
            return "unknown";
        }
	}

	private static String args;
	
	public static String getArgs() {
	    return args;
	}
	
	public static SAMProgramRecord buildSAMProgramRecord(String prog) {
        return buildSAMProgramRecord(prog, null);
    }
    public static SAMProgramRecord buildSAMProgramRecord(String prog, SAMFileHeader header) {
        String pgTemplate = "ngsutilsj:" + prog + "-";
        String pgID = pgTemplate;
        boolean found = true;
        int i = 0;
        
        SAMProgramRecord mostRecent = null;
        
        while (found) {
            found = false;
            i++;
            pgID = pgTemplate + i;
            if (header!=null) {
                for (SAMProgramRecord record: header.getProgramRecords()) {
                    if (mostRecent == null) {
                        mostRecent = record;
                    }
                    if (record.getId().equals(pgID)) {
                        found = true;
                    }
                }
            }
        }
        
        SAMProgramRecord programRecord = new SAMProgramRecord(pgID);
        programRecord.setProgramName("ngsutilsj:"+prog);
        programRecord.setProgramVersion(NGSUtils.getVersion());
        programRecord.setCommandLine("ngsutilsj " + NGSUtils.getArgs());
        if (mostRecent!=null) {
            programRecord.setPreviousProgramGroupId(mostRecent.getId());
        }
        return programRecord;
    }
}
