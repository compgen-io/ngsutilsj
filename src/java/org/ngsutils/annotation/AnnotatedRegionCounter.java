package org.ngsutils.annotation;

import java.io.IOException;

import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;

import org.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import org.ngsutils.bam.Strand;

public class AnnotatedRegionCounter {
    
    private long coding = 0;
    private long utr3 = 0;
    private long utr5 = 0;
    private long intron = 0;
    private long otherExon = 0;
    private long junction = 0;
    private long mitochrondrial = 0;
    private long intergenic = 0;

    private long codingRev = 0;
    private long utr3Rev = 0;
    private long utr5Rev = 0;
    private long intronRev = 0;
    private long otherExonRev = 0;
    
    protected final GTFAnnotationSource gtf;
    
    public AnnotatedRegionCounter(String filename) throws NumberFormatException, IOException {
         gtf = new GTFAnnotationSource(filename);
    }
    
    public void addRead(SAMRecord read) {
        if (read.getReferenceName().equals("chrM") || read.getReferenceName().equals("M")) {
            this.mitochrondrial++;
            return;
        }
        for (CigarElement op: read.getCigar().getCigarElements()) {
            if (op.getOperator().equals(CigarOperator.N)) {
                this.junction++;
                return;
            }
        }
        
        boolean genic = false;
        boolean exonic = false;
        boolean coding = false;
        boolean utr3 = false;
        boolean utr5 = false;
        
        boolean reversed = false;
        
        GenomeRegion readpos = GenomeRegion.getReadStartPos(read);
        for(GTFGene gene: gtf.findAnnotation(readpos)) {
            genic = true;
            if (gene.getStrand() != readpos.strand) {
                reversed = true;
            }

            for (GTFTranscript txpt: gene.getTranscripts()) {
                for (GTFExon exon: txpt.getExons()) {
                    if (exon.toRegion().contains(readpos)) {
                        exonic = true;
                        if (gene.getStrand() == Strand.PLUS) {
                            if (readpos.start < txpt.getCdsStart()) {
                                utr5 = true;
                            } else if (readpos.start > txpt.getCdsEnd()) {
                                utr3 = true;
                            } else {
                                coding = true;
                            }
                        } else {
                            if (readpos.start < txpt.getCdsStart()) {
                                utr3 = true;
                            } else if (readpos.start > txpt.getCdsEnd()) {
                                utr5 = true;
                            } else {
                                coding = true;
                            }
                        }
                    }
                }
            }
        }

        if (coding) {
            if (reversed) {
                this.codingRev++;
            } else {
                this.coding++;
            }
            return;
        }
        
        if (utr5) {
            if (reversed) {
                this.utr5Rev++;
            } else {
                this.utr5++;
            }
            return;
        }

        if (utr3) {
            if (reversed) {
                this.utr3Rev++;
            } else {
                this.utr3++;
            }
            return;
        }
        
        if (exonic) {
            if (reversed) {
                this.otherExonRev++;
            } else {
                this.otherExon++;
            }
            return;
        }

        if (genic) {
            if (reversed) {
                this.intronRev++;
            } else {
                this.intron++;
            }
            return;
        }
        this.intergenic++;
    }

    public long getCoding() {
        return coding;
    }

    public long getUtr3() {
        return utr3;
    }

    public long getUtr5() {
        return utr5;
    }

    public long getIntron() {
        return intron;
    }

    public long getOtherExon() {
        return otherExon;
    }

    public long getJunction() {
        return junction;
    }

    public long getMitochrondrial() {
        return mitochrondrial;
    }

    public long getIntergenic() {
        return intergenic;
    }

    public long getCodingRev() {
        return codingRev;
    }

    public long getUtr3Rev() {
        return utr3Rev;
    }

    public long getUtr5Rev() {
        return utr5Rev;
    }

    public long getIntronRev() {
        return intronRev;
    }

    public long getOtherExonRev() {
        return otherExonRev;
    }
}
