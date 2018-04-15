package io.compgen.ngsutils.cli.vcf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import htsjdk.samtools.util.CloseableIterator;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.TallyValues;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressStats;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCallOp;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-count", desc="For each variant in a VCF file, count the number of ref and alt alleles in a BAM file", category="vcf")
public class VCFCount extends AbstractOutputCommand {
    private String refFilename=null;
    private String vcfFilename=null;
    private String bamFilename=null;
    private String sampleName=null;
	
    private int maxDepth = -1;
    private int minMappingQual = 0;
    private int minBaseQual = 13;
    private boolean disableBAQ = true;
    private boolean extendedBAQ = false;
    
    private boolean onlyOutputPass = false;
    private boolean outputVCFAF = false;
    private boolean outputAF = false;

    private int filterFlags = 0;
    private int requiredFlags = 0;

    private int maxBatchLen = -1;

    @Option(desc = "Only keep properly paired reads", name = "proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
            filterFlags |= ReadUtils.MATE_UNMAPPED_FLAG;
        }
    }

    @Option(desc = "Batch variants into blocks of size {val} for mpileup", name = "batchlen")
    public void setBatchLen(int maxBatchLen) {
        this.maxBatchLen = maxBatchLen;
    }

    @Option(desc = "Filtering flags", name = "filter-flags", defaultValue = "3844")
    public void setFilterFlags(int flag) {
        filterFlags = flag;
    }

    @Option(desc = "Required flags", name = "required-flags", defaultValue = "0")
    public void setRequiredFlags(int flag) {
        requiredFlags = flag;
    }

    @Option(desc="Output variant allele frequency from the VCF file (requires AD FORMAT field)", name="vcf-af")
    public void setVCFAF(boolean val) {
        this.outputVCFAF = val;
    }

    @Option(desc="Output alternative allele frequency (from BAM file)", name="af")
    public void setAF(boolean val) {
        this.outputAF = val;
    }

    @Option(desc="BAQ re-calculation (default:false)", name="baq")
    public void setDisableBAQ(boolean val) {
        this.disableBAQ = !val;
    }

    @Option(desc="Perform extended BAQ re-calculation (default:false)", name="extended-baq")
    public void setExtendedBAQ(boolean val) {
        this.extendedBAQ = val;
    }

    @Option(desc="Minimum base quality (indels always returned regardless of base quality)", name="min-basequal", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    @Option(desc="Minimum read mapping quality (MAPQ)", name="min-mapq", defaultValue="0")
    public void setMinMapQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }
    
    @Option(desc="Maximum depth", name="max-depth", defaultValue="0")
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }

    @Option(desc="Sample to use for vcf-counts (if VCF file has more than one sample)", name="sample")
    public void setSampleName(String sampleName) {
        this.sampleName = sampleName;
    }
    
    @Option(desc="Reference FASTA file (optional, used to validate variant positions)", name="ref")
    public void setRefFilename(String refFilename) {
        this.refFilename = refFilename;
    }

    @UnnamedArg(name = "input.vcf input.bam", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames.length!=2) {
    	    throw new CommandArgumentException("You must include a VCF file and a BAM file.");
    	}
        vcfFilename = filenames[0];
        bamFilename = filenames[1];
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}

		if (outputVCFAF && !reader.getHeader().getFormatIDs().contains("AD")) {
	          throw new CommandArgumentException("The VCF file must contain the \"AD\" format annotation to output allele frequencies from the VCF file.");
		}

		int sampleIdx = 0;
        if (sampleName != null) {
            sampleIdx = reader.getHeader().getSamplePosByName(sampleName);
        }
		
		TabWriter writer = new TabWriter();
		
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## vcf-input: " + vcfFilename);
        writer.write_line("## bam-input: " + bamFilename);

        BAMPileup pileup = new BAMPileup(bamFilename);
        pileup.setFlagFilter(filterFlags);
        pileup.setFlagRequired(requiredFlags);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMaxDepth(maxDepth);
        pileup.setMinMappingQual(minMappingQual);
        pileup.setDisableBAQ(disableBAQ);
        pileup.setExtendedBAQ(extendedBAQ);
        pileup.setNoGaps(true);
        if (refFilename != null) {
            pileup.setRefFilename(refFilename);
        }

        writer.write_line("## pileup-cmd: " + StringUtils.join(" ", pileup.getCommand()));

        writer.write("chrom");
        writer.write("pos");
        writer.write("ref");
        writer.write("alt");
        if (outputVCFAF) {
            writer.write("vcf_ref_count");
            writer.write("vcf_alt_count");
        }
        writer.write("ref_count");
        writer.write("alt_count");
        if (outputAF) {
            writer.write("alt_freq");
        }
        writer.eol();

        long total = 0;
        final long[] offset = new long[]{0,0}; // offset, curpos
        
        for (String chr: reader.getHeader().getContigNames()) {
            total += reader.getHeader().getContigLength(chr);
        }
        
        final List<VCFRecord> recordBlock = new ArrayList<VCFRecord>();
        final VCFHeader header = reader.getHeader();
        final long totalF = total;
        
		for (VCFRecord record: IterUtils.wrap(ProgressUtils.getIterator(vcfFilename == "-" ? "variants <stdin>": vcfFilename, reader.iterator(), new ProgressStats(){

            @Override
            public long size() {
                return totalF;
            }

            @Override
            public long position() {
                return offset[0] + offset[1];
            }}, new ProgressMessage<VCFRecord>(){

                String curChrom = "";
                
                @Override
                public String msg(VCFRecord current) {
                    if (!current.getChrom().equals(curChrom)) {
                        if (!curChrom.equals("")) {
                            offset[0] += header.getContigLength(curChrom);
                        }
                        
                        curChrom = current.getChrom();
                    }

                    offset[1] = current.getPos();
                    
                    return current.getChrom()+":"+current.getPos();
                }}))) {
			if (onlyOutputPass && record.isFiltered()) {
				continue;
			}

			if (maxBatchLen > 0) {			
                if (recordBlock.size() > 0) {
                    if (!recordBlock.get(0).getChrom().equals(record.getChrom())) {
                        processVariants(recordBlock, pileup, writer, sampleIdx);
                        recordBlock.clear();
                    } else if (record.getPos() - recordBlock.get(0).getPos() > maxBatchLen) {
                        processVariants(recordBlock, pileup, writer, sampleIdx);
                        recordBlock.clear();
                    }
                }
    			
                recordBlock.add(record);
			} else {
			    processVariant(record, pileup, writer, sampleIdx);
			}
            

//            if (indel) {
//                System.out.print("INDEL: " + record.getChrom()+":"+record.getPos()+" Tally counts: ");
//                for (String t: tally.keySet()) {
//                    System.out.print(t+"="+tally.getCount(t)+" ");
//                }
//                System.out.println();
//            }
		
		}

		if (recordBlock.size() > 0) {
		    processVariants(recordBlock, pileup, writer, sampleIdx);
		}

		reader.close();
		writer.close();
	}

    private void processVariant(VCFRecord record, BAMPileup pileup, TabWriter writer, int sampleIdx) throws IOException {
        int pos = record.getPos() - 1; // switch to 0-based
        
        CloseableIterator<PileupRecord> it2 = pileup.pileup(new GenomeSpan(record.getChrom(), pos));
        for (PileupRecord pileupRecord: IterUtils.wrap(it2)) {
            if (pileupRecord.ref.equals(record.getChrom()) && pileupRecord.pos == pos) {
                if (refFilename != null && !pileupRecord.refBase.equals(record.getRef())) {
                    throw new IOException("Reference bases don't match! "+record.getChrom()+":"+record.getPos());
                }
                processVariantRecord(record, pileupRecord, writer, sampleIdx);
            }
        }
        it2.close();       

    }
    
    private void processVariantRecord(VCFRecord record, PileupRecord pileupRecord, TabWriter writer, int sampleIdx) throws IOException {
        if (refFilename != null && !pileupRecord.refBase.equals(record.getRef())) {
            throw new IOException("Reference bases don't match! "+record.getChrom()+":"+record.getPos());
        }
        
        TallyValues<String> tally = new TallyValues<String>();

        for (PileupBaseCall call: pileupRecord.getSampleRecords(0).calls) {
            if (call.op == PileupBaseCallOp.Match) {
                tally.incr(call.call);
            } else if (call.op == PileupBaseCallOp.Ins) {
                // NOTE: Indels are always reported out by BAMPileup/samtools mpileup 
                // Ex: C->CA  is a +1A in the pileup but C/CA in VCF
                //     chr8    109080640       C       4       ..-1A,, oJA<
                //     Should be 4 ref (C), 1 alt (-CA)

                tally.incr("ins"+record.getRef()+call.call); 
//                indel = true;

            } else if (call.op == PileupBaseCallOp.Del) {
                // Ex: CA->C  is a -1A in the pileup but CA/C in VCF
                //     chr8    109080640       C       4       ..-1A,, oJA<
                //     Should be 4 ref (C), 1 alt (-CA)

                tally.incr("del"+call.call.length()); 
//                indel = true;
            }
        }
        // for each alt-allele...
        for (int i=0; i< record.getAlt().size(); i++) {
            writer.write(record.getChrom());
            writer.write(record.getPos());
        
            writer.write(record.getRef());
            writer.write(record.getAlt().get(i));
        
            if (outputVCFAF) {
                try {
                    String ad = record.getSampleAttributes().get(sampleIdx).get("AD").asString(null);
                    String spl[] = ad.split(",");
                    writer.write(spl[0]);
                    writer.write(spl[i]);
                } catch (VCFAttributeException e) {
                    throw new IOException(e);
                }
            }
        
            long ref;
            long alt;
            
            if (record.getRef().length()>1) {
                // is a del
                // assumes a properly anchored variant call in VCF (ex: CA/C)
        
                ref = tally.getCount(record.getAlt().get(i));
                alt = tally.getCount("del"+(record.getRef().length()-1));
                
                
            } else if (record.getAlt().get(i).length()>1) {
                // is an insert
                ref = tally.getCount(record.getRef());
                alt = tally.getCount("ins"+record.getAlt().get(i));
            } else {
                // match
                ref = tally.getCount(record.getRef());
                alt = tally.getCount(record.getAlt().get(i));
            }
            
            writer.write(ref);
            writer.write(alt);
            
            if (outputAF) {
                writer.write(""+((double)alt) / (alt+ref));
            }
        
            writer.eol();
        }
        
    }

    private void processVariants(List<VCFRecord> records, BAMPileup pileup, TabWriter writer, int sampleIdx) throws IOException {
        // records must have the same chrom.
        
        int start = records.get(0).getPos() - 1;
        int end = records.get(records.size()-1).getPos(); // minus 1?
        
        GenomeSpan span = new GenomeSpan(records.get(0).getChrom(), start, end);
        CloseableIterator<PileupRecord> it2 = pileup.pileup(span);
        
        VCFRecord curRecord = records.get(0);
        int idx = 1;
        
        for (PileupRecord pileupRecord: IterUtils.wrap(it2)) {
            if (pileupRecord.ref.equals(curRecord.getChrom()) && pileupRecord.pos == curRecord.getPos()-1) {
                processVariantRecord(curRecord, pileupRecord, writer, sampleIdx);
                if (idx < records.size()) {
                    curRecord = records.get(idx++);
                } else {
                    break;
                }
            }
        }
        
        it2.close();

    }
    
}
