package io.compgen.ngsutils;

import java.io.IOException;
import java.util.Properties;

import io.compgen.cmdline.Help;
import io.compgen.cmdline.License;
import io.compgen.cmdline.MainBuilder;
import io.compgen.cmdline.exceptions.MissingCommandException;
import io.compgen.cmdline.exceptions.UnknownArgumentException;
import io.compgen.common.StringUtils;
import io.compgen.common.progress.SocketProgress;
import io.compgen.ngsutils.cli.annotate.GTFAnnotate;
import io.compgen.ngsutils.cli.annotate.RepeatAnnotate;
import io.compgen.ngsutils.cli.bam.BaiExplore;
import io.compgen.ngsutils.cli.bam.BamBaseCall;
import io.compgen.ngsutils.cli.bam.BamBest;
import io.compgen.ngsutils.cli.bam.BamCheck;
import io.compgen.ngsutils.cli.bam.BamClean;
import io.compgen.ngsutils.cli.bam.BamConcat;
import io.compgen.ngsutils.cli.bam.BamCount;
import io.compgen.ngsutils.cli.bam.BamCoverage;
import io.compgen.ngsutils.cli.bam.BamDiscord;
import io.compgen.ngsutils.cli.bam.BamExpressedRegions;
import io.compgen.ngsutils.cli.bam.BamExtract;
import io.compgen.ngsutils.cli.bam.BamFilterCli;
import io.compgen.ngsutils.cli.bam.BamFlagDuplicates;
import io.compgen.ngsutils.cli.bam.BamPhase;
import io.compgen.ngsutils.cli.bam.BamPhaseInformativeReads;
import io.compgen.ngsutils.cli.bam.BamReadGroup;
import io.compgen.ngsutils.cli.bam.BamRefCount;
import io.compgen.ngsutils.cli.bam.BamRemoveClipping;
import io.compgen.ngsutils.cli.bam.BamSampleReads;
import io.compgen.ngsutils.cli.bam.BamSoftClip;
import io.compgen.ngsutils.cli.bam.BamSort;
import io.compgen.ngsutils.cli.bam.BamSplit;
import io.compgen.ngsutils.cli.bam.BamStats;
import io.compgen.ngsutils.cli.bam.BamToBed;
import io.compgen.ngsutils.cli.bam.BamToBedGraph;
import io.compgen.ngsutils.cli.bam.BamToBedPE;
import io.compgen.ngsutils.cli.bam.BamToFasta;
import io.compgen.ngsutils.cli.bam.BamToFastq;
import io.compgen.ngsutils.cli.bam.BamVarCall;
import io.compgen.ngsutils.cli.bam.BamWPS;
import io.compgen.ngsutils.cli.bam.BinCount;
import io.compgen.ngsutils.cli.bam.PileupCli;
import io.compgen.ngsutils.cli.bed.BedCleanScore;
import io.compgen.ngsutils.cli.bed.BedCount;
import io.compgen.ngsutils.cli.bed.BedMerge;
import io.compgen.ngsutils.cli.bed.BedNearest;
import io.compgen.ngsutils.cli.bed.BedPEToBed;
import io.compgen.ngsutils.cli.bed.BedReduce;
import io.compgen.ngsutils.cli.bed.BedResize;
import io.compgen.ngsutils.cli.bed.BedSort;
import io.compgen.ngsutils.cli.bed.BedStats;
import io.compgen.ngsutils.cli.bed.BedToBed3;
import io.compgen.ngsutils.cli.bed.BedToBed6;
import io.compgen.ngsutils.cli.bed.BedToBedGraph;
import io.compgen.ngsutils.cli.bed.BedToBedPE;
import io.compgen.ngsutils.cli.bed.BedToFasta;
import io.compgen.ngsutils.cli.fasta.FastaBins;
import io.compgen.ngsutils.cli.fasta.FastaFilter;
import io.compgen.ngsutils.cli.fasta.FastaGC;
import io.compgen.ngsutils.cli.fasta.FastaGenerateReads;
import io.compgen.ngsutils.cli.fasta.FastaGrep;
import io.compgen.ngsutils.cli.fasta.FastaMask;
import io.compgen.ngsutils.cli.fasta.FastaMotif;
import io.compgen.ngsutils.cli.fasta.FastaNames;
import io.compgen.ngsutils.cli.fasta.FastaRandom;
import io.compgen.ngsutils.cli.fasta.FastaRevcomp;
import io.compgen.ngsutils.cli.fasta.FastaSplit;
import io.compgen.ngsutils.cli.fasta.FastaSubseq;
import io.compgen.ngsutils.cli.fasta.FastaTag;
import io.compgen.ngsutils.cli.fasta.FastaTri;
import io.compgen.ngsutils.cli.fasta.FastaWrap;
import io.compgen.ngsutils.cli.fastq.FastqBarcode;
import io.compgen.ngsutils.cli.fastq.FastqCheck;
import io.compgen.ngsutils.cli.fastq.FastqDemux;
import io.compgen.ngsutils.cli.fastq.FastqFilterCli;
import io.compgen.ngsutils.cli.fastq.FastqMerge;
import io.compgen.ngsutils.cli.fastq.FastqOverlap;
import io.compgen.ngsutils.cli.fastq.FastqRemix;
import io.compgen.ngsutils.cli.fastq.FastqSeparate;
import io.compgen.ngsutils.cli.fastq.FastqSort;
import io.compgen.ngsutils.cli.fastq.FastqSplit;
import io.compgen.ngsutils.cli.fastq.FastqStats;
import io.compgen.ngsutils.cli.fastq.FastqToBam;
import io.compgen.ngsutils.cli.fastq.FastqToFasta;
import io.compgen.ngsutils.cli.gtf.GTFExport;
import io.compgen.ngsutils.cli.gtf.GTFToFASTA;
import io.compgen.ngsutils.cli.gtf.GeneExport;
import io.compgen.ngsutils.cli.tab.BGZStats;
import io.compgen.ngsutils.cli.tab.TabAnnotate;
import io.compgen.ngsutils.cli.tab.TabixCat;
import io.compgen.ngsutils.cli.tab.TabixConcat;
import io.compgen.ngsutils.cli.tab.TabixQuery;
import io.compgen.ngsutils.cli.tab.TabixSplit;
import io.compgen.ngsutils.cli.tdf.TabJoin;
import io.compgen.ngsutils.cli.vcf.VCFAnnotateCmd;
import io.compgen.ngsutils.cli.vcf.VCFBedCount;
import io.compgen.ngsutils.cli.vcf.VCFBuild;
import io.compgen.ngsutils.cli.vcf.VCFCheck;
import io.compgen.ngsutils.cli.vcf.VCFChrFix;
import io.compgen.ngsutils.cli.vcf.VCFClearFilter;
import io.compgen.ngsutils.cli.vcf.VCFConcat;
import io.compgen.ngsutils.cli.vcf.VCFConcatN;
import io.compgen.ngsutils.cli.vcf.VCFConsensus;
import io.compgen.ngsutils.cli.vcf.VCFCount;
import io.compgen.ngsutils.cli.vcf.VCFEffect;
import io.compgen.ngsutils.cli.vcf.VCFExportCmd;
import io.compgen.ngsutils.cli.vcf.VCFFilterCmd;
import io.compgen.ngsutils.cli.vcf.VCFHeaderInfo;
import io.compgen.ngsutils.cli.vcf.VCFMerge;
import io.compgen.ngsutils.cli.vcf.VCFPeptide;
import io.compgen.ngsutils.cli.vcf.VCFRemoveInfoFlag;
import io.compgen.ngsutils.cli.vcf.VCFRenameSample;
import io.compgen.ngsutils.cli.vcf.VCFReorder;
import io.compgen.ngsutils.cli.vcf.VCFSVToFASTA;
import io.compgen.ngsutils.cli.vcf.VCFSampleExport;
import io.compgen.ngsutils.cli.vcf.VCFSamples;
import io.compgen.ngsutils.cli.vcf.VCFSplit;
import io.compgen.ngsutils.cli.vcf.VCFStats;
import io.compgen.ngsutils.cli.vcf.VCFStrip;
import io.compgen.ngsutils.cli.vcf.VCFToBED;
import io.compgen.ngsutils.cli.vcf.VCFToBEDPE;
import io.compgen.ngsutils.cli.vcf.VCFToCount;
import io.compgen.ngsutils.cli.vcf.VCFTsTvRatio;
import io.compgen.ngsutils.support.DigestCmd;
import io.compgen.ngsutils.support.stats.FisherCli;
import io.compgen.ngsutils.support.stats.YatesChiSqCli;
import io.compgen.ngsutils.tabix.BGZFCat;

@SuppressWarnings("deprecation")
public class NGSUtils {
    private static Properties properties = new Properties();
    static {
        try {
            properties.load(NGSUtils.class.getClassLoader().getResourceAsStream("io/compgen/ngsutils/ngsutilsj.properties"));
        } catch (IOException e) {
        }
    }
    
    public static void main(String[] args) {
        NGSUtils.args = StringUtils.join(" ", args);
        
        SocketProgress.setHeader("ngsutilsj - " + NGSUtils.args);
        MainBuilder main = new MainBuilder(false)
            .setProgName("ngsutilsj")
            .setHelpHeader("ngsutilsj - Data wrangling for NGS\n---------------------------------------")
            .setDefaultUsage("Usage: ngsutilsj cmd [options]")
            .setHelpFooter("http://compgen.io/ngsutilsj\n" + getVersion())
            .setCategoryOrder(new String[] { "bam", "bed", "fasta", "fastq", "gtf", "annotation", "vcf", "help"})
            .addCommand(License.class)
            .addCommand(Help.class)
            .addCommand(Version.class)
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
            .addCommand(GeneExport.class)
            .addCommand(FastqDemux.class)
            .addCommand(BamBaseCall.class)
            .addCommand(BamToBedGraph.class)
            .addCommand(BamExpressedRegions.class)
            .addCommand(FastqBarcode.class)
            .addCommand(VCFFilterCmd.class)
            .addCommand(VCFAnnotateCmd.class)
            .addCommand(VCFExportCmd.class)
            .addCommand(VCFToBED.class)
            .addCommand(VCFChrFix.class)
            .addCommand(TabixQuery.class)
            .addCommand(BGZFCat.class)
            .addCommand(VCFCount.class)
            .addCommand(VCFStrip.class)
            .addCommand(VCFToCount.class)
            .addCommand(TabixCat.class)
            .addCommand(TabAnnotate.class)
            .addCommand(YatesChiSqCli.class)
            .addCommand(FastaTri.class)
            .addCommand(VCFTsTvRatio.class)
            .addCommand(DigestCmd.class)
            .addCommand(BamRemoveClipping.class)
            .addCommand(VCFToBEDPE.class)
            .addCommand(BedStats.class)
        	.addCommand(BamRefCount.class)
        	.addCommand(VCFSVToFASTA.class)
        	.addCommand(BamExtract.class)
        	.addCommand(FastaRevcomp.class)
        	.addCommand(FastqOverlap.class)
    		.addCommand(BamSoftClip.class)
    		.addCommand(VCFStats.class)
    		.addCommand(VCFClearFilter.class)
    		.addCommand(BedToBedGraph.class)
    		.addCommand(VCFMerge.class)
    		.addCommand(FastaMotif.class)
			.addCommand(FastaRandom.class)
			.addCommand(VCFCheck.class)
			.addCommand(BamToBedPE.class)
			.addCommand(BedPEToBed.class)
			.addCommand(BamClean.class)
			.addCommand(BedMerge.class)
			.addCommand(VCFPeptide.class)
			.addCommand(FastaGrep.class)
			.addCommand(BamToFasta.class)
			.addCommand(BamPhase.class)
			.addCommand(TabJoin.class)
			.addCommand(FastaBins.class)
			.addCommand(VCFBedCount.class)
			.addCommand(VCFConsensus.class)
			.addCommand(VCFRenameSample.class)
			.addCommand(GTFToFASTA.class)
			.addCommand(VCFEffect.class)
			.addCommand(FastqRemix.class)
			.addCommand(BedSort.class)
			.addCommand(BedToBedPE.class)
			.addCommand(BamSort.class)
			.addCommand(BamVarCall.class)
			.addCommand(BamWPS.class)
			.addCommand(VCFBuild.class)
			.addCommand(VCFSamples.class)
			.addCommand(VCFRemoveInfoFlag.class)
			.addCommand(BaiExplore.class)
			.addCommand(BGZStats.class)
			.addCommand(TabixSplit.class)
			.addCommand(VCFSampleExport.class)
			.addCommand(TabixConcat.class)
			.addCommand(BamPhaseInformativeReads.class)
			.addCommand(VCFConcat.class)
        	.addCommand(VCFHeaderInfo.class)
    		.addCommand(VCFReorder.class)
    		.addCommand(VCFSplit.class)
    		.addCommand(VCFConcatN.class);


        try {
            if (args.length == 0) {
                main.showCommands();
            } else {
//                if (!getBuild().equals("dev")) {
//                    UpdateCheck uc = new UpdateCheck("http://updates.compgen.io/versions.txt", "NGSUTILSJ_NO_UPDATECHECK", "io.compgen.ngsutilsj.no_upatecheck");
//                    uc.setValue("cmd", args[0]);
//                    uc.setValue("os", System.getProperty("os.name"));
//                    uc.setValue("arch", System.getProperty("os.arch"));
//                    uc.setValue("java_version", System.getProperty("java.version"));
//                    uc.setValue("java_vendor", System.getProperty("java.vendor"));
//                    
//                    if (!uc.isCurrentVersion("ngsutilsj", getBuild(), getVersionCode())) {
//                        String desc = uc.getCurrentVersionDescription("ngsutilsj", getBuild());
//                        if (desc == null || desc.equals("")) {
//                            System.err.println("Updated version of ngsutilsj is available: "+uc.getCurrentVersion("ngsutilsj", getBuild()));
//                        } else {
//                            System.err.println("Updated version of ngsutilsj is available: "+uc.getCurrentVersion("ngsutilsj", getBuild()) + " ("+ desc + ")");
//                        }
//                    }
//                }
//
                main.findAndRun(args);
            }
        } catch (UnknownArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println();
            try {
                main.showCommandHelp(e.clazz);
            } catch (MissingCommandException e1) {
            }
            System.exit(1);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String getVersionCode() {
        return properties.getProperty("version");
    }

    
	public static String getVersion() {
        return "ngsutilsj-" + getVersionCode() + " (" + getBuild() + ")";
	}

    public static String getBuild() {
        return properties.getProperty("build");
    }

	
	private static String args;
	
	public static String getArgs() {
	    return args;
	}
}
