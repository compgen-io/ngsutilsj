package io.compgen.ngsutils;

import io.compgen.cmdline.Help;
import io.compgen.cmdline.License;
import io.compgen.cmdline.MainBuilder;
import io.compgen.cmdline.exceptions.MissingCommandException;
import io.compgen.cmdline.exceptions.UnknownArgumentException;
import io.compgen.common.StringUtils;
import io.compgen.common.phone.PhoneHome;
import io.compgen.common.progress.SocketProgress;
import io.compgen.ngsutils.cli.annotate.GTFAnnotate;
import io.compgen.ngsutils.cli.annotate.RepeatAnnotate;
import io.compgen.ngsutils.cli.bam.BamBest;
import io.compgen.ngsutils.cli.bam.BamCheck;
import io.compgen.ngsutils.cli.bam.BamConcat;
import io.compgen.ngsutils.cli.bam.BamCount;
import io.compgen.ngsutils.cli.bam.BamCoverage;
import io.compgen.ngsutils.cli.bam.BamDiscord;
import io.compgen.ngsutils.cli.bam.BamFilterCli;
import io.compgen.ngsutils.cli.bam.BamFlagDuplicates;
import io.compgen.ngsutils.cli.bam.BamReadGroup;
import io.compgen.ngsutils.cli.bam.BamSampleReads;
import io.compgen.ngsutils.cli.bam.BamSplit;
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
import io.compgen.ngsutils.cli.fasta.FastaGC;
import io.compgen.ngsutils.cli.fasta.FastaGenerateReads;
import io.compgen.ngsutils.cli.fasta.FastaMask;
import io.compgen.ngsutils.cli.fasta.FastaNames;
import io.compgen.ngsutils.cli.fasta.FastaSplit;
import io.compgen.ngsutils.cli.fasta.FastaSubseq;
import io.compgen.ngsutils.cli.fasta.FastaTag;
import io.compgen.ngsutils.cli.fasta.FastaWrap;
import io.compgen.ngsutils.cli.fastq.FastqCheck;
import io.compgen.ngsutils.cli.fastq.FastqFilterCli;
import io.compgen.ngsutils.cli.fastq.FastqMerge;
import io.compgen.ngsutils.cli.fastq.FastqSeparate;
import io.compgen.ngsutils.cli.fastq.FastqSort;
import io.compgen.ngsutils.cli.fastq.FastqSplit;
import io.compgen.ngsutils.cli.fastq.FastqStats;
import io.compgen.ngsutils.cli.fastq.FastqToBam;
import io.compgen.ngsutils.cli.fastq.FastqToFasta;
import io.compgen.ngsutils.cli.gtf.GTFExport;
import io.compgen.ngsutils.cli.gtf.GeneExport;
import io.compgen.ngsutils.cli.kmer.FastqKmer;
import io.compgen.ngsutils.cli.kmer.KmerDiff;
import io.compgen.ngsutils.cli.kmer.KmerMerge;
import io.compgen.ngsutils.support.stats.FisherCli;

import java.io.IOException;

@SuppressWarnings("deprecation")
public class NGSUtils {
    
    public static void main(String[] args) {
        NGSUtils.args = StringUtils.join(" ", args);
        
        SocketProgress.setHeader("ngsutilsj - " + NGSUtils.args);
        MainBuilder main = new MainBuilder()
            .setProgName("ngsutilsj")
            .setHelpHeader("ngsutilsj - Data wrangling for NGS\n---------------------------------------")
            .setDefaultUsage("Usage: ngsutilsj cmd [options]")
            .setHelpFooter("http://compgen.io/ngsutilsj\n" + getVersion())
            .setCategoryOrder(new String[] { "bam", "bed", "fasta", "fastq", "gtf", "annotation", "kmer", "help"})
            .addCommand(License.class)
            .addCommand(Help.class)
            .addCommand(FastqToBam.class)
            .addCommand(FastqToFasta.class)
            .addCommand(FastqSort.class)
            .addCommand(FastqMerge.class)
            .addCommand(FastqCheck.class)
            .addCommand(FastqSeparate.class)
            .addCommand(FastqSplit.class)
            .addCommand(FastqStats.class)
            .addCommand(FastqFilterCli.class)
            .addCommand(BinCount.class)
            .addCommand(BamBest.class)
            .addCommand(BamFlagDuplicates.class)
            .addCommand(BamSplit.class)
            .addCommand(BamCheck.class)
            .addCommand(BamCount.class)
            .addCommand(BamDiscord.class)
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
            .addCommand(FastaNames.class)
            .addCommand(FastaGenerateReads.class)
            .addCommand(FastaGC.class)
            .addCommand(FastaSplit.class)
            .addCommand(FastaMask.class)
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
            .addCommand(BamConcat.class)
            .addCommand(FastqKmer.class)
            .addCommand(KmerMerge.class)
            .addCommand(KmerDiff.class)
            .addCommand(GeneExport.class);

        try {
            if (args.length == 0) {
                main.showCommands();
            } else if (main.isValidCommand(args[0]) || args[0].equals("help")) {
                if (!getBuild().equals("dev")) {
                    PhoneHome ph = new PhoneHome("http://phonehome.compgen.io/versions", "NGSUTILSJ_NO_PHONEHOME");
                    ph.setValue("cmd", args[0]);
                    ph.setValue("os", System.getProperty("os.name"));
                    ph.setValue("arch", System.getProperty("os.arch"));
                    ph.setValue("java_version", System.getProperty("java.version"));
                    ph.setValue("java_vendor", System.getProperty("java.vendor"));
                    
                    if (!ph.isCurrentVersion("ngsutilsj", getBuild(), getVersionCode())) {
                        // TODO: Add suuport for a "last-check" to avoid notifying on each run
                        //       Need to write a temp file someplace?
                        String desc = ph.getCurrentVersionDescription("ngsutilsj", getBuild());
                        if (desc == null || desc.equals("")) {
                            System.err.println("Updated version of ngsutilsj is available: "+ph.getCurrentVersion("ngsutilsj", getBuild()));
                        } else {
                            System.err.println("Updated version of ngsutilsj is available: "+ph.getCurrentVersion("ngsutilsj", getBuild()) + " ("+ desc + ")");
                        }
                    }
                }

                main.findAndRun(args);
            } else {
                System.err.println("ERROR: Unknown command: " + args[0]);
                System.err.println();
                main.showCommands();
                System.exit(1);
            }
        } catch (UnknownArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println();
            System.exit(1);
            try {
                main.showCommandHelp(e.clazz);
            } catch (MissingCommandException e1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String getVersionCode() {
        try {
            return MainBuilder.readFile("io/compgen/ngsutils/VERSION");
        } catch (IOException e) {
            return "0";
        }
    }

    
	public static String getVersion() {
	    try {
            return "ngsutilsj-"+MainBuilder.readFile("io/compgen/ngsutils/VERSION")+" ("+getBuild()+")";
        } catch (IOException e) {
            return "ngsutilsj-unknown";
        }
	}

	   public static String getBuild() {
	        try {
	            return MainBuilder.readFile("io/compgen/ngsutils/BUILD");
	        } catch (IOException e) {
	            return "dev";
	        }
	    }

	
	private static String args;
	
	public static String getArgs() {
	    return args;
	}
}
