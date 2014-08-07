package org.ngsutils.cli.bam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.junction.JunctionDiff;
import org.ngsutils.junction.JunctionKey;
import org.ngsutils.junction.JunctionStats;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.stats.StatUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj junction-diff")
@Command(name="junction-diff", desc="Given counts files, find differentially spliced junctions", cat="bam")
public class JunctionDiffCli extends AbstractOutputCommand {
    private List<String> filenames;
    private Integer[] groups;
    
    private double maxEditDistance = -1;
    private int minTotalCount = -1;
    private boolean calcFDR = false; 
    
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

    @Option(description = "Calculate an empirical FDR value for each junction", longName="fdr")
    public void setCalcFDR(boolean calcFDR) {
        this.calcFDR = calcFDR;
    }

    @Option(description = "Comma-delimited list of groups in the same order as the files are given (1=control, 2=experimental, Example: --groups=1,1,1,2,2,2)", longName="groups")
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
        juncDiff.findJunctions(filenames, groups);
        
        List<Double> fdrDonorR1 = new ArrayList<Double>();
        List<Double> fdrAcceptorR1 = new ArrayList<Double>();
        List<Double> fdrDonorR2 = new ArrayList<Double>();
        List<Double> fdrAcceptorR2 = new ArrayList<Double>();

        if (calcFDR) {
            System.err.println("Calculating FDR...");
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
        writer.eol();

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
        if (calcFDR) {
            writer.write("FDR (B-H)");
        }
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
                        
                if (calcFDR) {
                    if (!juncDiff.isSplitReads() || key.read1) {
                        writer.write(fdrDonorR1.remove(0));
                    } else {
                        writer.write(fdrDonorR2.remove(0));
                    }
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
                if (calcFDR) {
                    if (!juncDiff.isSplitReads() || key.read1) {
                        writer.write(fdrAcceptorR1.remove(0));
                    } else {
                        writer.write(fdrAcceptorR2.remove(0));
                    }
                }
                
                writer.eol();
            }
        }

        writer.close();
    }
}

