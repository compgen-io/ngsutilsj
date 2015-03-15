package io.compgen.ngsutils.cli.splicing;

import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.NGSUtilsException;
import io.compgen.ngsutils.junction.JunctionCounts;
import io.compgen.ngsutils.junction.JunctionDiff;
import io.compgen.ngsutils.junction.JunctionDiffStats;
import io.compgen.ngsutils.junction.JunctionKey;
import io.compgen.ngsutils.junction.JunctionStats;
import io.compgen.ngsutils.junction.JunctionDiffStats.JunctionDiffSample;
import io.compgen.ngsutils.support.StringUtils;
import io.compgen.ngsutils.support.TabWriter;
import io.compgen.ngsutils.support.cli.AbstractOutputCommand;
import io.compgen.ngsutils.support.cli.Command;
import io.compgen.ngsutils.support.stats.StatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj junction-diff")
@Command(name="junction-diff", desc="Given counts files, find differentially spliced junctions", cat="splicing", experimental=true)
public class JunctionDiffCli extends AbstractOutputCommand {
    private List<String> filenames;
    private Integer[] groups;
    
    private double maxEditDistance = -1;
    private int minTotalCount = -1;
    
    @Unparsed(name = "FILEs")
    public void setFilename(List<String> filenames) {
        this.filenames = filenames;
    }

    @Option(description = "Require the average edit-distance to be below {value}", longName="max-edit-distance", defaultValue="-1")
    public void setMaxEditDistance(double maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
    }

    @Option(description = "Require more than {value} total number of reads crossing a junction", longName="min-total-count", defaultValue="-1")
    public void setMinTotalCount(int minTotalCount) {
        this.minTotalCount = minTotalCount;
    }

    @Option(description = "Comma-delimited list of groups in the same order as the files are given (1=control, 2=experimental, Example: --groups 1,1,1,2,2,2)", longName="groups")
    public void setGroups(String value) {
        List<Integer> tmp = new ArrayList<Integer>();
        for (String s:value.split(",")) {
            tmp.add(Integer.parseInt(s));
        }
        
        groups = tmp.toArray(new Integer[tmp.size()]);
        
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        
        JunctionDiff juncDiff = new JunctionDiff();
        juncDiff.setMinTotalCount(minTotalCount);
        juncDiff.setMaxEditDistance(maxEditDistance);
        JunctionDiffStats jdStats = juncDiff.findJunctions(filenames, groups);
        
        if (verbose) {
            System.err.println("Samples:");
            for (JunctionDiffSample sample: jdStats.getSamples()) {
                System.err.println("  " + sample.sampleName + " [" + sample.group + "] - " + sample.filename);
            }
            System.err.println("Junctions       : "+jdStats.getTotalJunctions());
            System.err.println("Filtered        : "+jdStats.getFilteredJunctions());
            System.err.println("Valid donors    : "+jdStats.getValidDonors());
            System.err.println("Valid acceptors : "+jdStats.getValidAcceptors());
            System.err.println("Final junctions : "+jdStats.getDonorAcceptorFilteredJunctions());
        }
        
        List<Double> fdrDonorR1 = new ArrayList<Double>();
        List<Double> fdrAcceptorR1 = new ArrayList<Double>();
        List<Double> fdrDonorR2 = new ArrayList<Double>();
        List<Double> fdrAcceptorR2 = new ArrayList<Double>();

        if (verbose) {
            System.err.println("Calculating FDR...");
        }
        List<Double> pvalueDonorR1 = new ArrayList<Double>();
        List<Double> pvalueAcceptorR1 = new ArrayList<Double>();
        List<Double> pvalueDonorR2 = new ArrayList<Double>();
        List<Double> pvalueAcceptorR2 = new ArrayList<Double>();

        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            if (juncDiff.getJunctions().get(key).isValidDonor()) {
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, true);
                double pvalue = juncDiff.calcPvalue(stats.tScore, key.read1,true);
                if (!juncDiff.isSplitReads() || key.read1) {
                    pvalueDonorR1.add(pvalue);
                } else {
                    pvalueDonorR2.add(pvalue);
                }
            }
            if (juncDiff.getJunctions().get(key).isValidAcceptor()) {
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, false);
                double pvalue = juncDiff.calcPvalue(stats.tScore, key.read1,false);
                if (!juncDiff.isSplitReads() || key.read1) {
                    pvalueAcceptorR1.add(pvalue);
                } else {
                    pvalueAcceptorR2.add(pvalue);
                }
            }
        }
            
        double[] pvals = new double[pvalueDonorR1.size()];
        for (int i=0; i<pvals.length; i++) {
            pvals[i] = pvalueDonorR1.get(i);
        }
        double[] fdr = StatUtils.benjaminiHochberg(pvals);
        for (int i=0; i<fdr.length; i++) {
            fdrDonorR1.add(fdr[i]);
        }

        pvals = new double[pvalueAcceptorR1.size()];
        for (int i=0; i<pvals.length; i++) {
            pvals[i] = pvalueAcceptorR1.get(i);
        }
        fdr = StatUtils.benjaminiHochberg(pvals);
        for (int i=0; i<fdr.length; i++) {
            fdrAcceptorR1.add(fdr[i]);
        }

        if (juncDiff.isSplitReads()) {
            pvals = new double[pvalueDonorR2.size()];
            for (int i=0; i<pvals.length; i++) {
                pvals[i] = pvalueDonorR2.get(i);
            }
            fdr = StatUtils.benjaminiHochberg(pvals);
            for (int i=0; i<fdr.length; i++) {
                fdrDonorR2.add(fdr[i]);
            }

            pvals = new double[pvalueAcceptorR2.size()];
            for (int i=0; i<pvals.length; i++) {
                pvals[i] = pvalueAcceptorR2.get(i);
            }
            fdr = StatUtils.benjaminiHochberg(pvals);
            for (int i=0; i<fdr.length; i++) {
                fdrAcceptorR2.add(fdr[i]);
            }
        }

        
        Set<String> uniqueJunctions = new HashSet<String>();
        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            JunctionCounts j = juncDiff.getJunctions().get(key);
            if (j.isValidDonor() || j.isValidAcceptor()) {
                uniqueJunctions.add(key.name);
            }
        }

        
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## files: " + StringUtils.join(",", filenames));
        writer.write_line("## groups: " + StringUtils.join(",", groups));

        if (minTotalCount > -1) { 
            writer.write_line("## min-total-count: " + minTotalCount);
        }
        
        if (maxEditDistance > 0) { 
            writer.write_line("## max-edit-distance: " + maxEditDistance);
        }

        if (juncDiff.isSplitReads()) {
            writer.write_line("## split-reads (summary counts are for R1 and R2)");
        }
        
        for (JunctionDiffSample sample: jdStats.getSamples()) {
            writer.write_line("## sample: " + sample.sampleName + ";" + sample.group + ";" + sample.filename);
        }

        writer.write_line("## total-junctions: "+jdStats.getTotalJunctions());
        writer.write_line("## filtered-junctions: "+jdStats.getFilteredJunctions());
        writer.write_line("## valid-donors: "+jdStats.getValidDonors());
        writer.write_line("## valid-acceptors: "+jdStats.getValidAcceptors());
        writer.write_line("## final-junctions: "+jdStats.getDonorAcceptorFilteredJunctions());
        writer.write_line("## unique-junctions: "+uniqueJunctions.size());
        uniqueJunctions.clear();

        writer.write("junction", "strand");
        if (juncDiff.isSplitReads()) {
            writer.write("read");
        }
        writer.write("site_type");
        writer.write("site");

        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_counts");
        }
        
        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_site_counts");
        }
        
        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_site_pct");
        }
        
        writer.write("control-pct", "exp-pct", "pct_diff", "tscore");
        writer.write("pvalue");
        writer.write("FDR (B-H)");
        writer.eol();

        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            if (juncDiff.getJunctions().get(key).isValidDonor()) {
                writer.write(key.name, key.strand.toString());
                if (juncDiff.isSplitReads()) {
                    writer.write(key.getReadNum());
                }
                writer.write("donor", key.donor.name);
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getCount(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getDonorTotal(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getDonorPct(i));
                }
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, true);
                writer.write(stats.controlPct);
                writer.write(stats.expPct);
                writer.write(stats.pctDiff);
                writer.write(stats.tScore);
                writer.write(juncDiff.calcPvalue(stats.tScore, key.read1,true));
                        
                if (!juncDiff.isSplitReads() || key.read1) {
                    writer.write(fdrDonorR1.remove(0));
                } else {
                    writer.write(fdrDonorR2.remove(0));
                }
                
                writer.eol();
            }
            if (juncDiff.getJunctions().get(key).isValidAcceptor()) {
                writer.write(key.name, key.strand.toString());
                if (juncDiff.isSplitReads()) {
                    writer.write(key.getReadNum());
                }
                writer.write("acceptor", key.acceptor.name);
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getCount(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getAcceptorTotal(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getAcceptorPct(i));
                }
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, false);
                writer.write(stats.controlPct);
                writer.write(stats.expPct);
                writer.write(stats.pctDiff);
                writer.write(stats.tScore);
                writer.write(juncDiff.calcPvalue(stats.tScore, key.read1,false));
                if (!juncDiff.isSplitReads() || key.read1) {
                    writer.write(fdrAcceptorR1.remove(0));
                } else {
                    writer.write(fdrAcceptorR2.remove(0));
                }
                
                writer.eol();
            }
        }

        writer.close();
    }
}

