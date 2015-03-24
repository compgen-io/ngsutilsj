package io.compgen.ngsutils.cli.splicing;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.junction.JunctionDonorAcceptor;
import io.compgen.ngsutils.junction.JunctionKey;
import io.compgen.support.StringLineReader;
import io.compgen.support.StringUtils;
import io.compgen.support.TabWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Command(name="find-events", desc="Merges differentially spliced junction counts (junction-diff) into events of related junctions", category="splicing", experimental=true)
public class FindEvents extends AbstractOutputCommand {
    public class JunctionEventStats {
        public final double pvalue;
        public final double pctdiff;
        
        public JunctionEventStats(double pvalue, double pctdiff) {
            this.pctdiff = pctdiff;
            this.pvalue = pvalue;
        }
    }

    private String filename = null;
    private String failedFilename = null;
    private String bedFilename = null;
    
    private double pctThreshold = 0.1;
    private double fdrThreshold = 0.1;

    private Set<String> used = new HashSet<String>();
    
    private Map<JunctionKey, JunctionEventStats> validJunctions = new HashMap<JunctionKey, JunctionEventStats>();
    private List<List<JunctionKey>> events = new ArrayList<List<JunctionKey>>();
    
    private Map<JunctionDonorAcceptor, List<JunctionKey>> donors = new HashMap<JunctionDonorAcceptor, List<JunctionKey>>();
    private Map<JunctionDonorAcceptor, List<JunctionKey>> acceptors = new HashMap<JunctionDonorAcceptor, List<JunctionKey>>();

    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Minimum FDR cut-off (default: 0.1)", name="fdr", defaultValue="0.1")
    public void setFDRThreshold(double val) {
        this.fdrThreshold = val;
    }

    @Option(desc="Output failed junctions here (BED)", name="failed")
    public void setFailedFilename(String failedFilename) {
        this.failedFilename = failedFilename;
    }

    @Option(desc="Output BED file for all junctions passing filters", name="bed")
    public void setBedFilename(String filename) {
        this.bedFilename = filename;
    }

    @Option(desc="Minimum pct-difference (default: 0.1)", name="pct-dff", defaultValue="0.1")
    public void setPctDiff(double val) {
        this.pctThreshold = val;
    }


    @Exec
    public void exec() throws IOException {
        
        String[] header = null;
        int juncIdx = -1;
        int siteTypeIdx = -1;
//        int siteIdx = -1;
        int pctIdx = -1;
        int fdrIdx = -1;
        int pvalueIdx = -1;
        int strandIdx = -1;
        
        Set<JunctionKey> allJunctions = new HashSet<JunctionKey>();
        
        StringLineReader reader = new StringLineReader(filename);
        for (String line: reader) {
            if (line != null && line.charAt(0) != '#') {
                String[] cols = StringUtils.strip(line).split("\t");
                if (header == null) {
                    // process header, look for column names, and assign column-indexes
                    header = cols;
                    
                    for (int i=0; i< header.length; i++) {
                        switch(header[i]) {
                        case "junction":
                            juncIdx = i;
                            break;
                        case "strand":
                            strandIdx = i;
                            break;
                        case "site_type":
                            siteTypeIdx = i;
                            break;
//                        case "site":
//                            siteIdx = i;
//                            break;
                        case "pct_diff":
                            pctIdx = i;
                            break;
                        case "pvalue":
                            pvalueIdx = i;
                            break;
                        case "FDR (B-H)":
                            fdrIdx = i;
                            break;
                        default:
                            break;
                        }
                    }
                } else {
                    // this is a junction line... find the key, if it is new, add a count object, 
                    // and add the counts for this sample.

                    double fdr = Double.parseDouble(cols[fdrIdx]);
                    double pct = Double.parseDouble(cols[pctIdx]);
                    double pvalue = Double.parseDouble(cols[pvalueIdx]);

                    JunctionKey junction = new JunctionKey(cols[juncIdx],Strand.parse(cols[strandIdx]));
                    allJunctions.add(junction);
                    
                    if (fdr > fdrThreshold || Math.abs(pct) < pctThreshold) {
                        continue;
                    }
                    
                    validJunctions.put(junction, new JunctionEventStats(pvalue, pct));
                    
                    boolean isDonor = cols[siteTypeIdx].equals("donor");
                    if (isDonor) {
                        if (!donors.containsKey(junction.donor)) {
                            donors.put(junction.donor, new ArrayList<JunctionKey>());
                        }
                        donors.get(junction.donor).add(junction);
                    } else {
                        if (!acceptors.containsKey(junction.acceptor)) {
                            acceptors.put(junction.acceptor, new ArrayList<JunctionKey>());
                        }
                        acceptors.get(junction.acceptor).add(junction);
                    }
                }
            }
        }               
        reader.close();
        
        if (failedFilename !=null) {
            TabWriter failed = new TabWriter(failedFilename);
            for (JunctionKey junc: allJunctions) {
                if (!validJunctions.containsKey(junc)) {
                    GenomeSpan region = GenomeSpan.parse(junc.name, true);
                    failed.write(region.ref, ""+region.start, ""+region.end, junc.name, "0", junc.strand.toString());
                    failed.eol();
                }
            }
            failed.close();
        }
                
        for (JunctionKey junction: validJunctions.keySet()) {
            startEvent(junction);
        }
        
        int multievents = 0;
        int soloevents = 0;

        for (List<JunctionKey> event: events) {
            if (event.size()==1) {
                soloevents++;
            } else {
                multievents++;
            }
        }
        
        TabWriter bed = null;
        
        if (bedFilename != null) {
            bed = new TabWriter(bedFilename);
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + filename);
        writer.write_line("## fdr-threshold: " + fdrThreshold);
        writer.write_line("## pct-threshold: " + pctThreshold);
        writer.write_line("## total-junctions: "+ allJunctions.size());
        writer.write_line("## passing-junctions: "+ validJunctions.size());
        writer.write_line("## passing-donors: "+ donors.size());
        writer.write_line("## passing-acceptors: "+ acceptors.size());
        writer.write_line("## multi-events: "+ multievents);
        writer.write_line("## solo-events: "+ soloevents);
        writer.write("event", "genome_span", "strand", "junction_count", "min_pvalue", "max_pctdiff", "retained_intron", "pvalues", "pctdiffs");
        writer.eol();

        for (List<JunctionKey> event: events) {
            double minPvalue = -1;
            double maxPctDiff = -1;
            
            String chrom = null;            
            int start = -1;
            int end = -1;
            boolean retainedIntron = false;
            Strand strand = Strand.NONE;
            
            List<Double> pvalues = new ArrayList<Double>();
            List<Double> pctdiffs = new ArrayList<Double>();
            for (JunctionKey junc: event) {
                GenomeSpan region = GenomeSpan.parse(junc.name, true);
                if (start == -1) {
                    chrom = region.ref;
                    start = region.start;
                    end = region.end;
                    strand = junc.strand;
                } else {
                    start = Math.min(start, region.start);
                    end = Math.max(end, region.end);
                }
                
                if (region.start == region.end) {
                    retainedIntron = true;
                }
                
                JunctionEventStats stats = validJunctions.get(junc);
                
                pvalues.add(stats.pvalue);
                pctdiffs.add(stats.pctdiff);
                
                if (minPvalue == -1 || stats.pvalue < minPvalue) {
                    minPvalue = stats.pvalue;
                }
                if (maxPctDiff == -1 || Math.abs(stats.pctdiff) > maxPctDiff) {
                    maxPctDiff = Math.abs(stats.pctdiff);
                }
                
                if (bed!=null) {
                    bed.write(chrom, ""+region.start, ""+region.end, region.toString(), ""+(Math.abs(stats.pctdiff)* 100), (stats.pctdiff > 0) ? "+":"-");
                    bed.eol();
                }
                
            }

            writer.write(StringUtils.join(";", event));
            writer.write(chrom+":"+start+"-"+end);
            writer.write(strand.toString());
            writer.write(event.size());
            writer.write(minPvalue);
            writer.write(maxPctDiff);
            writer.write(retainedIntron ? "Y": "N");
            writer.write(StringUtils.join(";", pvalues));
            writer.write(StringUtils.join(";", pctdiffs));
            writer.eol();
        }

        writer.close();
        if (bed!=null) {
            bed.close();
        }
    }
    
    private void startEvent(JunctionKey junction) {
        if (used.contains(junction.name)) {
            return;
        }
        List<JunctionKey> event = new ArrayList<JunctionKey>();
        populateEvent(junction, event);
        Collections.sort(event);
        events.add(event);
    }
    
    private void populateEvent(JunctionKey junction, List<JunctionKey> event) {
        if (used.contains(junction.name)) {
            return;
        }
        used.add(junction.name);
        event.add(junction);
        if (donors.containsKey(junction.donor)) {
            for (JunctionKey sib: donors.get(junction.donor)) {
                populateEvent(sib, event);
            }
        }

        if (acceptors.containsKey(junction.acceptor)) {
            for (JunctionKey sib: acceptors.get(junction.acceptor)) {
                populateEvent(sib, event);
            }
        }
    }
}

