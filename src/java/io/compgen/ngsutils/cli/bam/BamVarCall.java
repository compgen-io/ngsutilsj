package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloseableIterator;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.BaseTally;
import io.compgen.ngsutils.bam.support.BaseTally.BaseCount;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.fasta.IndexedFastaFile;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCallOp;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFContigDef;
import io.compgen.ngsutils.vcf.VCFFilterDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;

@Command(name="bam-varcall", 
		 desc="Simple variant caller based on Poisson statistics", 
		 category="bam", 
		 doc=     "This is a simple variant caller that finds variants present in a single sample.\n"
		 		+ "It can operate in diploid-germline mode (looking for homozygous and heterozygous\n"
		 		+ "SNVs), or in a somatic/tumor mode looking for any variant. The variants are called\n"
		 		+ "using the output of samtools mpileup and not a local re-alignment. As such, they\n"
		 		+ "are highly dependent upon the alignment tool used."
		 )

public class BamVarCall extends AbstractOutputCommand {

//    private String pileupFilename = null;
    private String fastaFilename = null;
	private String bamFilename = null;

    private int minDepth = 1;
    private int minAlt = 1;
    private double minAF = 0.1;
    private int maxDepth = -1;
	private int minBaseQual = 13;
	private int minMapQ = 0;

    private int requiredFlags = 0;
    private int filterFlags = 0;
	
    private String bedFilename = null;
    private String region = null;
    private String sampleName = null;
    
    private boolean passingOnly = false;
    private boolean gzipped = false;
    private boolean germlineMode = false; // use diploid assumptions for variants
    
    private String bedOutputTemplate = null;

    private VCFFilterDef filterMinDepth = new VCFFilterDef("CG_MIN_DEPTH", "Total depth too low");
    private VCFFilterDef filterMinAltCount = new VCFFilterDef("CG_MIN_ALT", "Alt count too low");
    private VCFFilterDef filterMinAF = new VCFFilterDef("CG_MIN_AF", "Alt AF too low");

    private VCFAnnotationDef infoDP = VCFAnnotationDef.newIntegerN(true, "DP", "Read depth", 1);
    private VCFAnnotationDef infoAF = VCFAnnotationDef.newFloatA(true, "AF", "Allele frequency");

    private VCFAnnotationDef formatGT = VCFAnnotationDef.newStringN(false, "GT", "Genotype", 1);
    private VCFAnnotationDef formatDP = VCFAnnotationDef.newIntegerN(false, "DP", "Read depth", 1);
    private VCFAnnotationDef formatAD = VCFAnnotationDef.newIntegerR(false, "AD", "Allele depth for ref and alt");
    private VCFAnnotationDef formatSAC = VCFAnnotationDef.newIntegerWild(false, "SAC", "Number of reads on the plus and minux strands for all alleles (including ref)");
    
    @Option(desc="If using a BED file, write the output to a separate file for each BED region", name="bed-output", helpValue="template.txt")
    public void setBedOutputTemplate(String bedOutputTemplate) {
        this.bedOutputTemplate = bedOutputTemplate;
    }

    @Option(desc="Sample name to use in the VCF (default: based on bam filename, for germline or tumor-only)", name="sample", helpValue="name")
    public void setSampleName(String sampleName) {
        this.sampleName = sampleName;
    }
    
    @Option(desc = "Only output passing variants", name = "passing")
    public void setOnlyPassing(boolean onlyPassing) {
    	this.passingOnly = onlyPassing;
    }
    
    @Option(desc = "GZip ompress output", name = "gz")
    public void setGzip(boolean gzipped) {
    	this.gzipped = gzipped;
    }
    
    @Option(desc = "Germline mode", name = "germline")
    public void setGermline(boolean val) {
    	this.germlineMode = val;
    }

    @Option(desc = "Only keep properly paired reads", name = "proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
        }
    }

    @Option(desc = "Only keep mapped reads (both reads if paired)", name = "mapped")
    public void setMapped(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_UNMAPPED_FLAG | ReadUtils.MATE_UNMAPPED_FLAG;
        }
    }

    @Option(desc = "No supplementary mappings", name = "no-supplementary")
    public void seNoSupplmentary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.SUPPLEMENTARY_ALIGNMENT_FLAG;
        }
    }

    @Option(desc = "No secondary mappings", name = "no-secondary")
    public void setNoSecondary(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.NOT_PRIMARY_ALIGNMENT_FLAG;
        }
    }

    @Option(desc = "No PCR duplicates", name = "no-pcrdup")
    public void setNoPCRDuplicates(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.DUPLICATE_READ_FLAG;
        }
    }

    @Option(desc = "No QC failures", name = "no-qcfail")
    public void setNoQCFail(boolean val) {
        if (val) {
            filterFlags |= ReadUtils.READ_FAILS_VENDOR_QUALITY_CHECK_FLAG;
        }
    }

    @Option(desc = "Filtering flags", name = "filter-flags", defaultValue = "0")
    public void setFilterFlags(int flag) {
        filterFlags |= flag;
    }

    @Option(desc = "Required flags", name = "required-flags", defaultValue = "0")
    public void setRequiredFlags(int flag) {
        requiredFlags |= flag;
    }
   
   @Option(desc="Minimum number of calls to call as a variant", name="min-alt", defaultValue="1")
   public void setMinAlt(int minAlt) {
       this.minAlt = minAlt;
   }
   
   @Option(desc="Minimum allele frequency", name="min-af")
   public void setMinAF(double minAF) {
       this.minAF = minAF;
   }
   
   @Option(desc="Minimum total depth required to make a call", name="min-depth", defaultValue="10")
   public void setMinDepth(int minDepth) {
       this.minDepth = minDepth;
   }

   @Option(desc="Maximum depth for samtools mpileup (default: use samtools default)", name="max-depth", defaultValue="-1")
   public void setMaxDepth(int maxDepth) {
       this.maxDepth = maxDepth;
   }

    @Option(desc="Minimum base quality", name="min-basequal", defaultValue="13")
    public void setMinBaseQual(int minBaseQual) {
    	this.minBaseQual = minBaseQual;
    }

    @Option(desc="Minimum read mapping quality (MAPQ)", name="min-mapq", defaultValue="0")
    public void setMinMapQual(int minMapQ) {
    	this.minMapQ = minMapQ;
    }
	
    @Option(desc="Reference genome FASTA file (faidx indexed)", name="ref", helpValue="fname", required=true)
    public void setFASTAFilename(String filename) {
        this.fastaFilename = filename;
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.bamFilename = filename;
    }

    @Option(desc="Regions BED file (output only bases within these regions)", name="bed", helpValue="fname")
    public void setBEDFilename(String filename) {
    	this.bedFilename = filename;
    }
    @Option(desc="Region to report base calls (chr:start-end)", name="region")
    public void setRegion(String region) {
    	this.region = region;
    }
    
	public BamVarCall() {
	}
	
	@Exec
	public void exec() throws Exception {
		if (bamFilename == null){
            throw new CommandArgumentException("You must specify a BAM file for input.");
		}

        if (bedFilename != null && region != null) {
            throw new CommandArgumentException("You can not specify both --region and --bed.");
        }
        

        SamReader bam = SamReaderFactory.makeDefault().open(new File(bamFilename));
        final SAMFileHeader header = bam.getFileHeader();
        
        BAMPileup pileup = new BAMPileup(bamFilename);
        pileup.setDisableBAQ(true);
        pileup.setExtendedBAQ(false);
        pileup.setFlagRequired(requiredFlags);
        pileup.setFlagFilter(filterFlags);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMinMappingQual(minMapQ);
        pileup.setMaxDepth(maxDepth);
//        pileup.setVerbose(verbose);
        if (fastaFilename != null) {
            pileup.setRefFilename(fastaFilename);
        }

        IndexedFastaFile fasta = new IndexedFastaFile(fastaFilename);
        
        VCFWriter writer=null;
        
        if (region != null) {
            writer = setupWriter(out, pileup, fasta);
            GenomeSpan span = GenomeSpan.parse(region);
            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, writer);
            it.close();
            writer.close();

        } else if (bedFilename != null){
			StringLineReader strReader = new StringLineReader(bedFilename);
			Set<String> chromMissingError = new HashSet<String>();
			int regionCount=0;
			
			if (bedOutputTemplate == null) {
	            writer = setupWriter(out, pileup, fasta);
			}
			
			for (String line: strReader) {
			    if (line.startsWith("#") || line.trim().length()==0) {
			        continue;
			    }
				String[] cols = StringUtils.strip(line).split("\t");
				String chrom = cols[0];
				int start = Integer.parseInt(cols[1]);
				int end = Integer.parseInt(cols[2]);
				String name = "region_"+(++regionCount);
				
				if (cols.length > 3) {
				    name = cols[3];
				}

				if (header.getSequence(chrom) == null) {
					if (!chromMissingError.contains(chrom)) {
						System.err.println("BAM file missing reference: " + chrom);
						chromMissingError.add(chrom);
					}
					continue;
				}

                if (bedOutputTemplate != null) {
                	if (gzipped) {
                		writer = setupWriter(new BufferedOutputStream(new FileOutputStream(bedOutputTemplate+name+".vcf")), pileup, fasta);
                	} else {
                		writer = setupWriter(new GZIPOutputStream(new FileOutputStream(bedOutputTemplate+name+".vcf.gz")), pileup, fasta);
                	}
                }

                GenomeSpan span = new GenomeSpan(chrom, start, end);

                if (verbose) {
					System.err.println(name+" "+span);
				}
				
                CloseableIterator<PileupRecord> it = pileup.pileup(span);
	            writePileupRecords(it, writer);
				it.close();

                if (bedOutputTemplate != null) {
                    writer.close();
                }
			}
            if (bedOutputTemplate == null) {
                writer.close();
            }
			strReader.close();
		} else {
		    // output everything

            writer = setupWriter(out, pileup, fasta);
			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, writer);
			it.close();
	        writer.close();
		}
	}

    private VCFWriter setupWriter(OutputStream out, BAMPileup pileup, IndexedFastaFile fasta) throws IOException {
    	VCFHeader header = new VCFHeader();
    	
    	for (String ref: fasta.getReferenceNames()) {
    		header.addContig(new VCFContigDef(ref, fasta.getReferenceLength(ref)));
    	}
    	
    	if (sampleName != null) {
    		header.addSample(sampleName);
    	} else {
    		header.addSample(new File(bamFilename).getName().replaceAll(".bam$", ""));
    	}
    	
    	header.addFilter(new VCFFilterDef("PASS", "All filters passed"));
    	
    	header.addFilter(filterMinDepth);
    	header.addFilter(filterMinAltCount);
    	header.addFilter(filterMinAF);    	
    	
    	header.addInfo(infoDP);
    	header.addInfo(infoAF);

    	header.addFormat(formatGT);
    	header.addFormat(formatDP);
    	header.addFormat(formatAD);
    	header.addFormat(formatSAC);

    	header.addLine("##reference=file://"+new File(fastaFilename).getAbsolutePath());
    	header.addLine("##ngsutilsj_program_version=" + NGSUtils.getVersion());
    	header.addLine("##ngsutilsj_cmd=" + NGSUtils.getArgs());
    	header.addLine("##ngsutilsj_pileup_cmd=" + StringUtils.join(" ", pileup.getCommand()));

    	return new VCFWriter(out, header);
    }

    private void writePileupRecords(CloseableIterator<PileupRecord> it, VCFWriter writer) throws IOException, VCFAttributeException, VCFParseException {
        int counter = 0;

        
        for (PileupRecord record: IterUtils.wrap(it)) {
        	if (record.getSampleRecords(0).coverage > minDepth) {
                writeRecord(record, writer);
            }

            counter++;
            if (verbose && counter >= 100000) {
                System.err.println(record.ref+":"+record.pos);
                counter = 0;
            }
        }
    }

    private void writeRecord(PileupRecord pileup, VCFWriter writer) throws IOException, VCFAttributeException, VCFParseException {

        BaseTally bt = new BaseTally();
        
        // initial pass through pileup results
        
        for (int sample=0; sample < pileup.getSampleCount(); sample++) {        
	        for (PileupBaseCall call: pileup.getSampleRecords(sample).calls) {
				if (call.op == PileupBaseCallOp.Match) {
					bt.incrStrand(call.call, call.plusStrand);
				} else if (call.op == PileupBaseCallOp.Ins) {
					bt.incrStrand("+"+call.call, call.plusStrand);
				} else if (call.op == PileupBaseCallOp.Del) {
					bt.incrStrand("-"+call.call, call.plusStrand);
				} else {
					// we don't care about gaps here.
				}
	        }
        }
        
        int nonRef = bt.nonRefSum(pileup.refBase);

        if (nonRef == 0) {
        	// we only return alt records
        	return;
        }
        
        List<BaseCount> calls = bt.getSorted();
        
        String refBase = pileup.refBase;
        String refSuffix = "";

        // determine the ref strings

        for (BaseCount call: calls) {
        	if (call.getCount()>0) { // these will all be > 0
        		String base = call.getBase();
        		if (base.charAt(0) == '-') {
        			// these are the bases that are deleted
        			//
        			// so, if we have a deletion and an SNV at the same point
        			// 		ex: ACCC > A; A > G;
        			// we can use "ACCC" as the ref for both
        			//          ACCC    A,GCCC
        			// 
        			// if we have insert and del at the same point:
        			//      ex: ACCC > A; A > ATTT;
        			// we'd have
        			//          ACCC    A,ATTTCCC
        			//
        			// each line can have ONLY ONE ref value.
        			//
        			// if we have more than one deletion...
        			//      ex: ACCC > A; ACCCG > A
        			// we need to use the larger one
        			//          ACCCG    A,AG
        			
        			String xtra = base.substring(1);
        			if (xtra.length() > refSuffix.length()) {
        				refSuffix = xtra;
        				refBase = pileup.refBase + refSuffix;
        			}
        		}
        	}
        }

        VCFRecord record = new VCFRecord(pileup.ref, pileup.pos + 1, refBase); // VCF is one-based


        List<Pair<Integer, String>> altCounts = new ArrayList<Pair<Integer, String>>(calls.size()+1);

        // determine the alt strings
        
        for (BaseCount call: calls) {
        	if (call.getCount()>0) { // these will all be > 0
        		String base = call.getBase();
        		if ((base+refSuffix).equals(refBase)) {
        			//System.out.println("base: "+base+ ", refSuffix: "+refSuffix+", refBase: "+refBase);
        			// the original reference call...
//        			altCounts.add(new Pair<Integer, String>(call.getCount(), ""));
        			continue;
        		}

        		if (base.charAt(0) == '+') {
        			altCounts.add(new Pair<Integer, String>(call.getCount(), pileup.refBase + base.substring(1) + refSuffix));
        		} else if (base.charAt(0) == '-') {
        			String xtra = base.substring(1);
        			
        			if (xtra.length() < refSuffix.length()) {
        				// we will be less than or equal
        				// ex: ACCC > A and ACCCG > A
        				//   refSuffix will be CCCG
        				// for ACCC > A, xtra will be:
        				//    CCC
        				// and the proper output is:    
        				//    ACCCG    AG
        				
            			altCounts.add(new Pair<Integer, String>(call.getCount(), pileup.refBase + refSuffix.substring(xtra.length())));
        			} else {
            			altCounts.add(new Pair<Integer, String>(call.getCount(), pileup.refBase));
        			}
        		} else {
        			// simple snv -- add the suffix if needed
        			altCounts.add(new Pair<Integer, String>(call.getCount(), base + refSuffix));
        		}
        	}
        }

        altCounts.sort(new Comparator<Pair<Integer, String>>(){
			@Override
			public int compare(Pair<Integer, String> o1, Pair<Integer, String> o2) {
				// note: this is in descending order
				if (o1.one > o2.one) {
					return -1;
				}
				if (o1.one < o2.one) {
						return 1;
				}
				if (o1.two.compareTo(o2.two) > 0) {
					return -1;
				}
				if (o1.two.compareTo(o2.two) < 0) {
						return 1;
				}
				return 0;
			}});

        
        // determine the alt order and remove any low-depth calls (keep at least one alt call if possible)
        
        int altAlleles = 0;
        int goodAlleles = 0;
        for (int i=0; i<altCounts.size(); i++) {
        	int count = altCounts.get(i).one;
        	String alt = altCounts.get(i).two; // either the alt allele or "" for ref
        	if (!"".equals(alt) ) {
	        	altAlleles++;
	        	if (count >= minAlt) {
	        		goodAlleles++;
	        	}
        	}
        }
        
        if (goodAlleles==0 && altAlleles > 0) {
        	goodAlleles = 1; // keep at least one altAllele, even if it isn't a good one.
        	                 // otherwise, only report back the "good" alleles
        }
                
        for (int i=0; i<goodAlleles; i++) {
        	String alt = altCounts.get(i).two; // add only the good alleles for alt
        	record.addAlt(alt);
        }
        
        int totalDP = 0;
        int totalAltDP = 0;
        int[] sampleAltDPs = new int[pileup.getSampleCount()]; 
        
        // sample level numbers here...
        for (int sampleNum=0; sampleNum < pileup.getSampleCount(); sampleNum++) {
            BaseTally bt2 = new BaseTally();
            
            for (int sample=0; sample < pileup.getSampleCount(); sample++) {        
    	        for (PileupBaseCall call: pileup.getSampleRecords(sample).calls) {
    				if (call.op == PileupBaseCallOp.Match) {
    					bt2.incrStrand(call.call, call.plusStrand);
    				} else if (call.op == PileupBaseCallOp.Ins) {
    					bt2.incrStrand("+"+call.call, call.plusStrand);
    				} else if (call.op == PileupBaseCallOp.Del) {
    					bt2.incrStrand("-"+call.call, call.plusStrand);
    				} else {
    					// we don't care about gaps here.
    				}
    	        }
            }
            
            int refPlus = 0;
            int refMinus = 0;
            
            int[] altPlus = new int[record.getAlt().size()];
            int[] altMinus = new int[record.getAlt().size()];
            
            for (BaseCount call: bt2.getSorted()) {	            
	            if (call.getCount()>0) { // these will all be > 0
	        		String base = call.getBase();
	        		if ((base+refSuffix).equals(refBase)) {
	        			refPlus += call.getStrandCount(Strand.PLUS);
	        			refMinus += call.getStrandCount(Strand.MINUS);
	        			continue;
	        		}
	
	        		if (base.charAt(0) == '+') {
	        			base = pileup.refBase + base.substring(1) + refSuffix;
	        		} else if (base.charAt(0) == '-') {
	        			String xtra = base.substring(1);
	        			
	        			if (xtra.length() < refSuffix.length()) {
	        				// we will be less than or equal
	        				// ex: ACCC > A and ACCCG > A
	        				//   refSuffix will be CCCG
	        				// for ACCC > A, xtra will be:
	        				//    CCC
	        				// and the proper output is:    
	        				//    ACCCG    AG
	        				
	        				base = pileup.refBase + refSuffix.substring(xtra.length());	
	        			} else {
	        				base = pileup.refBase;
	        			}
	        		} else {
	        			// simple snv -- add the suffix if needed
	        			base  = base + refSuffix;
	        		}
	        		
	        		for (int i=0; i<record.getAlt().size(); i++) {
	        			if (base.equals(record.getAlt().get(i))) {
	        				altPlus[i] += call.getStrandCount(Strand.PLUS);
	        				altMinus[i] += call.getStrandCount(Strand.MINUS);
	        			}
	        		}
	        	}
            }
            
            int sampleDP = refPlus + refMinus;
            int sampleAltDP = 0;
            
            String SAC = refPlus+","+refMinus;
            String AD = ""+(refPlus + refMinus);
            List<Pair<Integer, Integer>> sampleAltCounts = new ArrayList<Pair<Integer, Integer>>(record.getAlt().size()+1);
            sampleAltCounts.add(new Pair<Integer, Integer>(refPlus + refMinus, 0));

            for (int i=0; i< altPlus.length; i++) {
            	SAC += "," + altPlus[i] + "," + altMinus[i];
            	AD += "," + (altPlus[i] + altMinus[i]);
                sampleAltCounts.add(new Pair<Integer, Integer>(altPlus[i] + altMinus[i], i+1));
                sampleDP += altPlus[i] + altMinus[i];
                sampleAltDP += altPlus[i] + altMinus[i];
            }
            
            sampleAltCounts.sort(new Comparator<Pair<Integer, Integer>>(){
				@Override
				public int compare(Pair<Integer, Integer> o1, Pair<Integer, Integer> o2) {
					// note: this is in descending order
					if (o1.one > o2.one) {
						return -1;
					}
					if (o1.one < o2.one) {
							return 1;
					}
					if (o1.two > o2.two) {
						return -1;
					}
					if (o1.two < o2.two) {
							return 1;
					}
					return 0;
				}});

            String GT = "";
            int major = -1;
            int minor = -1;
            int majorIdx = 0;
            int minorIdx = 0;

            for (Pair<Integer, Integer> pair: sampleAltCounts) {
            	if (major == -1) {
            		major = pair.one;
            		majorIdx = pair.two;
            	} else if (minor == -1) {
            		minor = pair.one;
            		minorIdx = pair.two;
            	}
            }
            
            if (minor < this.minAlt || ((double)minor / (major+minor)) < this.minAF) {
            	GT = majorIdx + "/" + majorIdx;
            } else {
            	GT = majorIdx + "/" + minorIdx;            	
            }

            VCFAttributes format = new VCFAttributes();
            format.put("GT", new VCFAttributeValue(GT));
            format.put("DP", new VCFAttributeValue(sampleDP));
            format.put("AD", new VCFAttributeValue(AD));
            format.put("SAC", new VCFAttributeValue(SAC));
            record.addSampleAttributes(format);
            totalDP += sampleDP;
            totalAltDP += sampleAltDP;
            sampleAltDPs[sampleNum] = sampleAltDP;
        }
        // Note: these two filters are across all samples (of which, there should only be one)
        if (totalAltDP < minAlt) {
        	record.addFilter(filterMinAltCount.id);
        }

        if (totalDP < minDepth) {
        	record.addFilter(filterMinDepth.id);
        }

        boolean anyPassingAF = false;
        String infoAF = "";
        for (int i=0; i<sampleAltDPs.length; i++) {
        	if (i > 0) {
        		infoAF += ",";
        	}
        	
        	double af = (double)sampleAltDPs[i] / totalDP;

            if (af >= minAF) {
            	anyPassingAF = true;
            }

        	infoAF += String.format("%.3f", af); 
        }

        if (!anyPassingAF) {
        	record.addFilter(filterMinAF.id);
        }
        
        // across all samples
        VCFAttributes info = new VCFAttributes();
        info.put("DP", new VCFAttributeValue(totalDP));
        info.put("AF", new VCFAttributeValue(infoAF));
        record.setInfo(info);


        if (!passingOnly || !record.isFiltered()) {
        	writer.write(record);
        }
    }
}
