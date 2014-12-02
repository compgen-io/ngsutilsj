package org.ngsutils.annotation;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

import java.io.IOException;

import org.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;
import org.ngsutils.bam.support.ReadUtils;

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
    private long junctionRev = 0;

    protected final GTFAnnotationSource gtf;
    
    public AnnotatedRegionCounter(String filename) throws NumberFormatException, IOException {
        System.err.print("Loading GTF annotation: "+filename+"...");
        gtf = new GTFAnnotationSource(filename);
        System.err.println(" done");
    }
    
    public void addRead(SAMRecord read) {
        
        if (read.getReferenceName().equals("chrM") || read.getReferenceName().equals("M")) {
            this.mitochrondrial++;
            return;
        }

        boolean isGene = false;
        boolean isExon = false;
        boolean isCoding = false;
        boolean isJunction = false;
        boolean isUtr3 = false;
        boolean isUtr5 = false;

        boolean isGeneRev = false;
        boolean isExonRev = false;
        boolean isCodingRev = false;
        boolean isUtr3Rev = false;
        boolean isUtr5Rev = false;

        
        for (CigarElement op: read.getCigar().getCigarElements()) {
            if (op.getOperator().equals(CigarOperator.N)) {
                isJunction = true;
                break;
            }
        }
        
        GenomeRegion readpos = GenomeRegion.getReadStartPos(read);
        Strand readStrand = ReadUtils.getFragmentEffectiveStrand(read, Orientation.UNSTRANDED);

        for(GTFGene gene: gtf.findAnnotation(readpos)) {
            isGene = true;

            if (gene.getStrand() != readStrand) {
                isGeneRev = true;
            }

            for (GTFTranscript txpt: gene.getTranscripts()) {
                for (GTFExon cds: txpt.cds) {
                    if (cds.toRegion().contains(readpos)) {
                        isExon = true;
                        isCoding = true;
                        if (gene.getStrand() != readStrand) {
                            isCodingRev = true;
                        }

                    } else {
                        if (gene.getStrand() == Strand.PLUS) {
                            if (readpos.start < txpt.getCdsStart()) {
                                isUtr5 = true;
                                if (gene.getStrand() != readStrand) {
                                    isUtr5Rev = true;
                                }
                            } else if (readpos.start > txpt.getCdsEnd()) {
                                isUtr3 = true;
                                if (gene.getStrand() != readStrand) {
                                    isUtr3Rev = true;
                                }
                            }
                        } else {
                            if (readpos.start < txpt.getCdsStart()) {
                                isUtr3 = true;
                                if (gene.getStrand() != readStrand) {
                                    isUtr3Rev = true;
                                }
                            } else if (readpos.start > txpt.getCdsEnd()) {
                                isUtr5 = true;
                                if (gene.getStrand() != readStrand) {
                                    isUtr5Rev = true;
                                }
                            }
                        }
                    }
                }
                if (!isExon) {
                    for (GTFExon exon: txpt.exons) {
                        if (exon.toRegion().contains(readpos)) {
                            isExon = true;
                            if (gene.getStrand() != readStrand) {
                                isExonRev = true;
                            }
                        }
                    }
                }
            }
        }

        if (isJunction && isGene) {
            if (isGeneRev) {
                junctionRev++;
            } else {
                junction++;
            }
            return;
        }
        
        if (isCoding) {
            if (isCodingRev) {
                codingRev++;
            } else {
                coding++;
            }
            return;
        }
        
        if (isUtr5) {
            if (isUtr5Rev) {
                utr5Rev++;
            } else {
                utr5++;
            }
            return;
        }

        if (isUtr3) {
            if (isUtr3Rev) {
                utr3Rev++;
            } else {
                utr3++;
            }
            return;
        }
        
        if (isExon) {
            if (isExonRev) {
                otherExonRev++;
            } else {
                otherExon++;
            }
            return;
        }

        if (isGene) {
            if (isGeneRev) {
                intronRev++;
            } else {
                intron++;
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
    
    public long getJunctionRev() {
        return junctionRev;
    }
}
