package org.ngsutils.cli.bam;

import java.io.IOException;

import net.sf.samtools.SAMFileReader.ValidationStringency;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Pileup;
import org.ngsutils.bam.Pileup.PileupPos;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.TallyCounts;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-coverage")
@Command(name="bam-coverage", desc="Scans an aligned BAM file and calculates the number of reads covering each base", cat="bam", experimental=true)
public class BamCoverage extends AbstractOutputCommand {
    private String filename = null;
    private GenomeRegion region = null;
    
    private int minMappingQual=0;
    private int minBaseQual=13;
    
    private int requiredFlags = 0;
    private int filterFlags = 1796;
    
    private boolean paired = false;

    private boolean lenient = false;
    private boolean silent = false;

    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Only count within this region", longName="region", defaultToNull=true)
    public void setRegion(String region) {
        if (region != null) {
            this.region = GenomeRegion.parse(region);
        }
    }

    @Option(description = "Only count properly paired reads", longName="paired")
    public void setPaired(boolean val) {
        this.paired = val;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    
    @Option(description = "Minimum base-quality (default: 13)", longName="base-qual", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(description = "Minimum read mapping-quality (default: 0)", longName="map-qual", defaultValue="0")
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    @Option(description = "Required flags", longName="flags-req", defaultValue="0")
    public void setRequiredFlags(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    @Option(description = "Filter flags (Default: 1796)", longName="flags-filter", defaultValue="1796")
    public void setFilterFlags(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input BAM filename!");
        }
        
        TallyCounts tally = new TallyCounts(true);

        Pileup pileup = new Pileup(filename);
        if (lenient) {
            pileup.getReader().setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            pileup.getReader().setValidationStringency(ValidationStringency.SILENT);
        }

        if (paired) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG | ReadUtils.READ_PAIRED_FLAG;
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG; 
        }

        pileup.setMinMappingQual(minMappingQual);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setFlagFilter(filterFlags);
        pileup.setFlagRequired(requiredFlags);
        
        int lastRef = -1;
        
        if (region != null && verbose) {
            System.err.println("Region: "+region.toString());
        }
        
        for (PileupPos pileupPos: pileup.pileup(region)) {
            if (verbose && pileupPos.refIndex != lastRef) {
                System.err.println(pileup.getReader().getFileHeader().getSequence(pileupPos.refIndex).getSequenceName());
                lastRef = pileupPos.refIndex;
            }
            tally.incr(pileupPos.getCoverage());
        }
        
        pileup.close();
        tally.write(out);
    }
}
