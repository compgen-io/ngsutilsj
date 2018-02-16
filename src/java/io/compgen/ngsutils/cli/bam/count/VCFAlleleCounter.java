package io.compgen.ngsutils.cli.bam.count;

import java.io.IOException;
import java.util.Iterator;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCallOp;
import io.compgen.ngsutils.vcf.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.vcf.VCFReader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;

public class VCFAlleleCounter {
    private VCFReader reader;
    
    private String refFilename = null;
    private int maxDepth = -1;
    private int minMappingQual = -1;
    private int minBaseQual = -1;
    private int filterFlags = 0;
    private int requiredFlags = 0;
    
    private boolean disableBAQ = true;
    private boolean extendedBAQ = false;
    
    public VCFAlleleCounter(String vcfFilename, String refFilename) throws IOException {
        try {
            reader = new VCFReader(vcfFilename);
            if (reader.getHeader().getFormatDef("AD") == null) {
                throw new IOException("VCF file is missing the AD format annotation!");
            }
        } catch (VCFParseException e) {
            throw new IOException(e);
        }
        this.refFilename = refFilename;
    }

    
    public void count(String bamFilename, TabWriter writer, String sampleName) throws IOException {
        writer.write("chrom");
        writer.write("pos");
        writer.write("ref");
        writer.write("alt");
        writer.write("ref_count");
        writer.write("alt_count");
        writer.write("bam_ref_count");
        writer.write("bam_alt_count");
        writer.eol();

        int sampleIdx = 0;
        if (sampleName != null) {
            sampleIdx = reader.getHeader().getSamplePosByName(sampleName);
        }
        
        BAMPileup pileup = new BAMPileup(bamFilename);
        pileup.setFlagFilter(filterFlags);
        pileup.setFlagRequired(requiredFlags);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMaxDepth(maxDepth);
        pileup.setMinMappingQual(minMappingQual);
        pileup.setDisableBAQ(disableBAQ);
        pileup.setExtendedBAQ(extendedBAQ);
        pileup.setNoGaps(true);
        pileup.setRefFilename(refFilename);
        
        Iterator<VCFRecord> it = reader.iterator();
        for (VCFRecord record: IterUtils.wrap(it)) {

            try {
                String ad = record.getSampleAttributes().get(sampleIdx).get("AD").asString(null);

                writer.write(record.getChrom());
                writer.write(record.getPos());

                // TODO: handle indels
                if (record.getRef().length()>1) {
                    // del
                }
                if (record.getAlt().get(0).length()>1) {
                    // ins
                }
                writer.write(record.getRef());
                writer.write(record.getAlt().get(0));
    
                String spl[] = ad.split(",");
                writer.write(spl[0]);
                writer.write(spl[1]);
            
            } catch (VCFAttributeException e) {
                throw new IOException(e);
            }
            
            
            CloseableIterator<PileupRecord> it2 = pileup.pileup(new GenomeSpan(record.getChrom(), record.getPos()));
            for (PileupRecord pileupRecord: IterUtils.wrap(it2)) {
                if (pileupRecord.ref.equals(record.getChrom()) && pileupRecord.pos == record.getPos()) {
                    if (!pileupRecord.refBase.equals(record.getRef())) {
                        throw new IOException("Reference bases don't match! "+record.getChrom()+":"+record.getPos());
                    }
                    
                    int refCount = 0;
                    int altCount = 0;
                    
                    for (PileupBaseCall call: pileupRecord.getSampleRecords(0).calls) {
                        if (call.op == PileupBaseCallOp.Match) {
                            if (call.call.equals(record.getRef())) {
                                refCount++;
                            } else if (call.call.equals(record.getAlt().get(0))) {
                                altCount++;
                            }
                        } else if (call.op == PileupBaseCallOp.Ins) {
                            //TODO: handle this (requires figuring out if this is an indel from VCF above too!
                            System.err.println("Insert?   " + call.call);
                        } else if (call.op == PileupBaseCallOp.Del) {
                            //TODO: handle this (requires figuring out if this is an indel from VCF above too!
                            System.err.println("Deletion? " + call.call);
                        }
                    }
                    
                    writer.write(refCount);
                    writer.write(altCount);
                    writer.eol();
                    break;
                }
            }
            it2.close();
        }
    }
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    public void setFlagFilter(int filterFlags) {
        this.filterFlags = filterFlags;
    }

    public void setFlagRequired(int requiredFlags) {
        this.requiredFlags = requiredFlags;
    }

    public void setDisableBAQ(boolean val) {
        this.disableBAQ = val;
    }

    public void setExtendedBAQ(boolean val) {
        this.extendedBAQ = val;
    }

    public void setRefFilename(String refFilename) {
        this.refFilename = refFilename;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
}
