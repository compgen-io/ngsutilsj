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
import io.compgen.ngsutils.cli.bam.BamStats;
import io.compgen.ngsutils.cli.bam.BamToFastq;
import io.compgen.ngsutils.cli.bam.BinCount;
import io.compgen.ngsutils.cli.bam.PileupCli;
import io.compgen.ngsutils.cli.fasta.FastaCLI;
import io.compgen.ngsutils.cli.fastq.FastqFilterCli;
import io.compgen.ngsutils.cli.fastq.FastqMerge;
import io.compgen.ngsutils.cli.fastq.FastqSeparate;
import io.compgen.ngsutils.cli.fastq.FastqSort;
import io.compgen.ngsutils.cli.fastq.FastqSplit;
import io.compgen.ngsutils.cli.fastq.FastqToBam;
import io.compgen.ngsutils.cli.gtf.GTFExport;
import io.compgen.ngsutils.cli.splicing.FastaJunctions;
import io.compgen.ngsutils.cli.splicing.FindEvents;
import io.compgen.ngsutils.cli.splicing.JunctionCount;
import io.compgen.ngsutils.cli.splicing.JunctionDiffCli;

import java.io.IOException;

@SuppressWarnings("deprecation")
public class NGSUtils {
    
    public static void main(String[] args) throws Exception {
        NGSUtils.args = StringUtils.join(" ", args);
        SocketProgress.setHeader("NGSUtilsJ - " + NGSUtils.args);
        
        new MainBuilder()
            .setProgName("ngsutilsj")
            .setHelpHeader("NGSUtilsJ - Data wrangling for NGS\n---------------------------------------")
            .setDefaultUsage("Usage: ngsutilsj cmd [options]")
            .setHelpFooter("http://compgen.io/ngsutilsj\n"+MainBuilder.readFile("VERSION"))
            .addCommand(License.class)
            .addCommand(Help.class)
            .addCommand(FastqToBam.class)
            .addCommand(FastqSort.class)
            .addCommand(FastqMerge.class)
            .addCommand(FastqSeparate.class)
            .addCommand(FastqSplit.class)
            .addCommand(FastqFilterCli.class)
            .addCommand(BinCount.class)
            .addCommand(BamCheck.class)
            .addCommand(BamCount.class)
            .addCommand(BamCoverage.class)
            .addCommand(BamToFastq.class)
            .addCommand(BamFilterCli.class)
            .addCommand(BamStats.class)
            .addCommand(JunctionCount.class)
            .addCommand(FindEvents.class)
            .addCommand(JunctionDiffCli.class)
            .addCommand(FastaJunctions.class)
            .addCommand(FastaCLI.class)
            .addCommand(PileupCli.class)
            .addCommand(RepeatAnnotate.class)
            .addCommand(GTFAnnotate.class)
            .addCommand(GTFExport.class)
            .findAndRun(args);
    }

	public static String getVersion() {
	    try {
            return MainBuilder.readFile("VERSION");
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
