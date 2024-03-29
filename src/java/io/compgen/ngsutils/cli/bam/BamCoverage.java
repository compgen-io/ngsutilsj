package io.compgen.ngsutils.cli.bam;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TallyCounts;
import io.compgen.ngsutils.annotation.BedAnnotationSource;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;

@Command(name="bam-coverage", desc="Scans an aligned BAM file and calculates the number of reads covering each base", category="bam", experimental=true, 
    doc = "Note: This will not properly count bases with zero coverage at the start/end \n"
        + "of a chrom/reference. Those will be silently ignored. Zero coverage bases \n"
        + "within a chromosome or BED region will be properly tallied. Zero coverage \n"
        + "bases at the start/end of BED regions will also be properly tallied.\n")
public class BamCoverage extends AbstractOutputCommand {
    private String filename = null;
    private String bedFilename = null;
    private String all = null;
    
    private GenomeSpan region = null;
    
    private int minMappingQual=0;
    private int minBaseQual=13;
    private int maxDepth = 1000;
        
    private int requiredFlags = 0;
    private int filterFlags = 1796;
    
    private boolean paired = false;
    private boolean nogaps = false;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Max depth for pileup", name="max-depth", defaultValue="8000")
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    @Option(desc="Use these regions to count (BED format)", name="bed")
    public void setBedFilename(String bedFilename) {
        this.bedFilename = bedFilename;
    }
    
    @Option(desc="Only count within this region", name="region")
    public void setRegion(String region) {
        if (region != null) {
            this.region = GenomeSpan.parse(region);
        }
    }

    @Option(desc="Output all coverage values (default: a summary of quantiles)", name="all", helpValue="fname")
    public void setAll(String fname) {
        this.all = fname;
    }

    @Option(desc="Don't count gaps (RNA)", name="no-gaps")
    public void setNoGaps(boolean val) {
        this.nogaps = val;
    }    

    @Option(desc="Only count properly paired reads", name="paired")
    public void setPaired(boolean val) {
        this.paired = val;
    }    

    @Option(desc="Minimum base-quality (default: 13)", name="base-qual", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(desc="Minimum read mapping-quality (default: 0)", name="map-qual", defaultValue="0")
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    @Option(desc="Required flags", name="flags-req", defaultValue="0")
    public void setRequiredFlags(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    @Option(desc="Filter flags (Default: 1796)", name="flags-filter", defaultValue="1796")
    public void setFilterFlags(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        
        TallyCounts tally = new TallyCounts();
        BAMPileup pileup = new BAMPileup(filename);
        
        if (paired) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG | ReadUtils.READ_PAIRED_FLAG;
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
        }

        pileup.setMinMappingQual(minMappingQual);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setFlagFilter(filterFlags);
        pileup.setFlagRequired(requiredFlags);
        pileup.setNoGaps(nogaps);
        pileup.setMaxDepth(maxDepth);

        BedAnnotationSource bed = null;
        
        if (bedFilename != null) {
            pileup.setBedFilename(bedFilename);
            bed = new BedAnnotationSource(bedFilename);
        }
        
        Iterator<PileupRecord> it;
        if (region != null) {
            it = pileup.pileup(region);
        } else {
            it = pileup.pileup();
        }
        
        int lastPos = -1;

        GenomeSpan lastRegion = null;

        int tmp = 0;
        for (PileupRecord record: IterUtils.wrap(it)) {
            tmp++;
            if (verbose && tmp > 100000) {
                System.err.println(record.ref+":"+record.pos+" "+record.getSampleDepth(0));
                tmp = 0;
            }
            if (bed!=null) {
                for (BedRecord ann : bed.findAnnotation(new GenomeSpan(record.ref, record.pos))) {
                    GenomeSpan curRegion = ann.getCoord();
                    if (lastRegion != null && !curRegion.equals(lastRegion)) {
                        for (int i=lastPos+1; i<=lastRegion.end; i++) {
                            tally.incr(0);
                        }
                        if (record.pos > curRegion.start) {
                            for (int i=curRegion.start; i<record.pos; i++) {
                                tally.incr(0);
                            }
                        }
                    }
                    lastRegion = curRegion;
                    break;
                }
            }
            
            if (record.isBlank()) {
                tally.incr(0);
            } else {
                tally.incr(record.getSampleRecords(0).calls.size());
            }
            lastPos = record.pos;
        }

        if (lastRegion!=null) {
            for (int i=lastPos+1; i<=lastRegion.end; i++) { 
                tally.incr(0);
            }
        }

        if (all != null) {
        	FileOutputStream allOut = new FileOutputStream(all);
            tally.write(allOut);
            allOut.close();
        }
        out.write(("Min\t"+tally.getMin()+"\n").getBytes());
        for (double pct: new double[]{0.01, 0.05, 0.25, 0.5, 0.75, 0.95, 0.99}) {
            out.write((pct+"\t" + tally.getQuantile(pct)+"\n").getBytes());
        }
        out.write(("Max\t"+tally.getMax()+"\n").getBytes());
    }
}
