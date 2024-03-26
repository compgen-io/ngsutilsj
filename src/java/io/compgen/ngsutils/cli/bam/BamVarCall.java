package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.BaseTally;
import io.compgen.ngsutils.bam.support.BaseTally.BaseCount;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.support.stats.StatUtils;

@Command(name="bam-varcall", 
		 desc="For  a BAM file, call variants from a reference genome (germline, tumor-only, or tumor/normal).\n"
		 		+ "\n"
		 		+ "For germline calls, the p-value refers to if the call is:\n"
		 		+ "    (a) real AND\n"
		 		+ "    (b) either hetero/homozygous\n"
		 		+ "\n"
		 		+ "For tumor-only calls, the p-value refers to if the call is:\n"
		 		+ "    (a) real\n"
		 		+ "\n"
		 		+ "For tumor-normal calls, the p-value refers to if the call is:\n"
		 		+ "    (a) real AND\n"
		 		+ "    (b) not a hetero/homozygous in the normal", 
		 category="bam", experimental=true
		 )

public class BamVarCall extends AbstractOutputCommand {

    private String fastaFilename = null;
	private String bam1Filename = null;
	private String bam2Filename = null;

	private String bedFilename = null;
	private String region = null;

    private int minDepth = 1;
    private int minAlt = 0;
    private int maxNormalAlt = -1;
    private int minNormalDepth = 0;
    private int clusterDist = -1;
    
    private double minAF = 0.0;
//    private double minPvalue = 0.01;
    private double errorRate = 0.02;
    private double maxNormalAF = 1.0;
    
    private boolean passingOnly = false;
    private boolean noFilters = false;
    private boolean tumorOnly = false;
    
    private int maxDepth = -1;
	private int minBaseQual = 13;
	private int minMapQ = 0;

    private int requiredFlags = 0;
    private int filterFlags = 0;
    
    
    @Option(desc = "Use tumor-only statistics for variants calling (set when not including a normal BAM file)", name = "tumor-only")
    public void setTumorOnly(boolean val) {
    	this.tumorOnly = val;
    }

    @Option(desc = "Only output variants that pass filters", name = "passing")
    public void setPassingOnly(boolean val) {
    	this.passingOnly = val;
    }

    @Option(desc = "Do not include filters in the VCF output", name = "no-filters")
    public void setNoFilters(boolean val) {
    	this.noFilters = val;
    }

    @Option(desc = "Only keep properly paired reads", name = "proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
        }
    }

    @Option(desc = "Only keep reads where pairs are both mapped", name = "mapped")
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

    @Option(desc = "Filtering flags (mpileup)", name = "filter-flags", defaultValue = "0")
    public void setFilterFlags(int flag) {
        filterFlags |= flag;
    }

    @Option(desc = "Required flags (mpileup)", name = "required-flags", defaultValue = "0")
    public void setRequiredFlags(int flag) {
        requiredFlags |= flag;
    }   
    
//    @Option(desc="Minimum p-value for genotype prediction", name="min-pval", defaultValue="0.01")
//    public void setMinPvalue(double pval) {
//        this.minPvalue = pval;
//    }
    @Option(desc="Assumed error rate of sequencing (how many errors are expected)", name="error-rate", defaultValue="0.02")
    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }
    
   @Option(desc="Minimum allele-frequency to report", name="min-af")
   public void setMinAF(double minAF) {
       this.minAF = minAF;
   }
   
   @Option(desc="Maximum normal allele-frequency", name="max-normal-af")
   public void setMaxNormalAF(double maxNormalAF) {
       this.maxNormalAF = maxNormalAF;
   }
   
   @Option(desc="Minimum number of alt/variant calls", name="min-alt")
   public void setMinAlt(int minAlt) {
       this.minAlt = minAlt;
   }
   
   @Option(desc="Maximum number of non-reference (alt) calls to allow in the normal sample", name="max-normal")
   public void setMaxNormalAlt(int maxNormalAlt) {
       this.maxNormalAlt = maxNormalAlt;
   }
   
   @Option(desc="Minimum depth to output", name="min-depth", defaultValue="1")
   public void setMinDepth(int minDepth) {
       this.minDepth = minDepth;
   }

   @Option(desc="Minimum normal depth (tumor/normal)", name="min-normal-depth", defaultValue="0")
   public void setMinNormalDepth(int minNormalDepth) {
       this.minNormalDepth = minNormalDepth;
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
	
    @Option(desc="Flag variants within this distance from each other", name="cluster-dist")
    public void setClusterDist(int clusterDist) {
    	this.clusterDist = clusterDist;
    }
	
    @Option(desc="Reference genome FASTA file (optional, faidx indexed)", name="ref", helpValue="fname", required=true)
    public void setFASTAFilename(String filename) throws CommandArgumentException {
        this.fastaFilename = filename;
        
        if (!new File(filename+".fai").exists()) {
        	throw new CommandArgumentException("Missing FAI index for reference FASTA: "+filename);
        }        
    }

    @UnnamedArg(name = "[BAM | TUMOR NORMAL]")
    public void setFilename(String[] filenames) {
//    	String[] spl = filenames.split(" ");
    	this.bam1Filename = filenames[0];
    	if (filenames.length > 1) {
    		this.bam2Filename = filenames[1];
    	}
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
		if (bam1Filename == null){
            throw new CommandArgumentException("You must specify a BAM file for input.");
		}

        if (bedFilename != null && region != null) {
            throw new CommandArgumentException("You can not specify both --region and --bed.");
        }

        if (minAlt>0 && minDepth == 0) {
            throw new CommandArgumentException("You must set --min-depth > 0 when --min-alt is > 0");
        }

        if (fastaFilename==null) {
            throw new CommandArgumentException("You must set --fasta.");
        }
        

        SamReader bam = SamReaderFactory.makeDefault().open(new File(bam1Filename));
        final SAMFileHeader header = bam.getFileHeader();
        
        BAMPileup pileup;
        if (bam2Filename != null) {
        	pileup = new BAMPileup(bam1Filename, bam2Filename);
        } else {
        	pileup = new BAMPileup(bam1Filename);
        }

        pileup.setDisableBAQ(true);
        pileup.setExtendedBAQ(false);
        pileup.setFlagRequired(requiredFlags);
        pileup.setFlagFilter(filterFlags);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMinMappingQual(minMapQ);
        pileup.setMaxDepth(maxDepth);
//        pileup.setVerbose(verbose);
        pileup.setRefFilename(fastaFilename);
        TabWriter writer=null;

        
        if (region != null) {
            writer = setupWriter(out, pileup);
            GenomeSpan span = GenomeSpan.parse(region);
            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, writer);
            it.close();
            writer.close();

        } else if (bedFilename != null){
			StringLineReader strReader = new StringLineReader(bedFilename);
			Set<String> chromMissingError = new HashSet<String>();
			int regionCount=0;
			
            writer = setupWriter(out, pileup);
			
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

				GenomeSpan span = new GenomeSpan(chrom, start, end);

                if (verbose) {
					System.err.println(name+" "+span);
				}
				
                CloseableIterator<PileupRecord> it = pileup.pileup(span);
                writePileupRecords(it, writer);
				it.close();

			}
            writer.close();
			strReader.close();
		} else {
		    // output everything

            writer = setupWriter(out, pileup);
			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, writer);
			it.close();
	        writer.close();
		}
	}

    private TabWriter setupWriter(OutputStream out, BAMPileup pileup) throws IOException {
        TabWriter writer = new TabWriter(out);
        writer.write_line("##fileformat=VCFv4.2");

        writer.write_line("##FILTER=<ID=PASS,Description=\"All filters passed\">");
        writer.write_line("##FILTER=<ID=min_depth,Description=\"Depth in sample too low (" + this.minDepth+")\">");
        writer.write_line("##FILTER=<ID=min_abs_count,Description=\"Alt read count too low (" + this.minAlt+")\">");
        writer.write_line("##FILTER=<ID=min_af,Description=\"Allele-frequency too low (" + this.minAF+")\">");

        writer.write_line("##FILTER=<ID=multi_indel,Description=\"Multiple indels reported at position\">");
        writer.write_line("##FILTER=<ID=multi_alleles,Description=\"More than 3 alleles reported at position\">");

        if (this.clusterDist > 0) {
            writer.write_line("##FILTER=<ID=cluster_var,Description=\"Variants are clustered (w/in " + this.clusterDist+"bp)\">");
        }
        
        if (pileup.getFileCount() > 1) {
        	writer.write_line("##FILTER=<ID=low_pred_call_tumor,Description=\"Tumor/Normal variant prediction not above background\">");
//        	writer.write_line("##FILTER=<ID=pred_call_in_normal,Description=\"Variant predicted found in germline\">");

	        writer.write_line("##FILTER=<ID=min_normal_depth,Description=\"Depth in normal too low (" + this.minNormalDepth+")\">");
	        writer.write_line("##FILTER=<ID=max_normal_abs_count,Description=\"Alt read count in normal too high (" + this.maxNormalAlt+")\">");
	        writer.write_line("##FILTER=<ID=max_normal_af,Description=\"Allele-frequency in normal too high (" + this.maxNormalAF+")\">");
        } else {
            if (tumorOnly) {
            	writer.write_line("##FILTER=<ID=low_pred_call_tumor,Description=\"Tumor-only variant prediction not above background\">");
            } else {
            	writer.write_line("##FILTER=<ID=low_pred_call_germline,Description=\"Germline variant prediction not above background\">");
            }
        }

        writer.write_line("##FORMAT=<ID=AD,Number=R,Type=Integer,Description=\"Allelic depths for the ref and alt alleles (in same order)\">");
        writer.write_line("##FORMAT=<ID=AF,Number=A,Type=Float,Description=\"Allele fraction of alt alleles\">");
		writer.write_line("##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Approximate read depth in sample\">");
		writer.write_line("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">");
		writer.write_line("##FORMAT=<ID=SAC,Number=.,Type=Integer,Description=\"Number of reads on the forward and reverse strand supporting each allele (including reference)\">");        
        
        
		writer.write_line("##INFO=<ID=DP,Number=1,Type=Integer,Description=\"Total depth across all samples\">");
		writer.write_line("##INFO=<ID=AD,Number=R,Type=Integer,Description=\"Allelic depths for the ref and alt alleles (in same order)\">");
		
        if (pileup.getFileCount() > 1) {
        	writer.write_line("##SAMPLE=<ID=TUMOR,File="+new File(bam1Filename).getAbsolutePath()+">");
        	writer.write_line("##SAMPLE=<ID=NORMAL,File="+new File(bam2Filename).getAbsolutePath()+">");
        	
        } else {
        	writer.write_line("##SAMPLE=<ID=SAMPLE,File="+new File(bam1Filename).getAbsolutePath()+">");
        	
        }

        for (String line: new StringLineReader(fastaFilename+".fai")) {
        	String[] cols = line.split("\t");
        	writer.write_line("##contig=<ID="+cols[0]+",length="+cols[1]+">");
        }
        
        writer.write_line("##reference=file://"+new File(fastaFilename).getAbsolutePath());
        writer.write_line("##pileup_cmd="+StringUtils.join(" ", pileup.getCommand()));
        
        writer.write_line("##ngsutilsj_version=" + NGSUtils.getVersion());
        writer.write_line("##ngsutilsj_cmd=" + NGSUtils.getArgs());
        
        if (bedFilename != null) {
              writer.write_line("##bed-regions=" + bedFilename);
        } else if (region != null) {
              writer.write_line("##region=" + region);
        }

        writer.eol();

        writer.write("#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT");
        if ( pileup.getFileCount() > 1) {
        	writer.write("TUMOR");
        	writer.write("NORMAL");
        } else {
        	writer.write("SAMPLE");
        }
        writer.eol();
        return writer;
    }

    private void writePileupRecords(CloseableIterator<PileupRecord> it,  TabWriter writer) throws IOException {
    	int counter = 0;
        for (PileupRecord record: IterUtils.wrap(it)) {
//        	System.out.println(record.getSampleRecords(0).calls+" :: " +record.originalLine);
            if (record.getSampleRecords(0).calls!=null && record.getSampleRecords(0).calls.size() > 0) {
                writeVCFRecord(record, writer);
            }

            counter++;
            if (verbose && counter >= 1_000_000) {
                System.err.println(record.ref+":"+record.pos);
                counter = 0;
            }
        }
    }

    private void writeVCFRecord(PileupRecord record, TabWriter writer) throws IOException {
    	
    	if (record.refBase.equals("N")) {
    		// don't export anything when the reference base is 'N'.
    		return;
    	}

    	BaseTally bt = BaseTally.parsePileupRecord(record.getSampleRecords(0).calls);    	
    	BaseTally bt2 = null;    	

    	List<String> normCalls = null;

        if (record.getNumSamples() > 1 && record.getSampleRecords(1).calls != null && record.getSampleRecords(1).calls.size()>0) {
        	// this is a tumor/normal call, so we need to find the potential normal variants
        	// first. We will then subtract these from the tumor alt calls. 
        	
        	bt2 = BaseTally.parsePileupRecord(record.getSampleRecords(1).calls);
        	List<BaseCount> sorted = bt2.getSorted();
        	Collections.reverse(sorted); // we want descending order here.

        	int total = 0;
            for (BaseCount bc: sorted) {
            	total += bc.getCount();
            }
            
            for (BaseCount bc: sorted) {
        		if (bc.getCount()>0) {
        			double hom = StatUtils.calcPvalueHomozygous(bc.getCount(), total, this.errorRate);
        			double het = StatUtils.calcPvalueHeterozygous(bc.getCount(), total);
        			double bg = StatUtils.calcPvalueBackground(bc.getCount(), total, this.errorRate);
        			
        			if (hom > het && hom > bg) {
        				// homozygous call
        				if (normCalls == null) {
        					normCalls = new ArrayList<String>();
        					normCalls.add(bc.getBase());
        				}
        			} else if (het > hom && het > bg) {
        				// heterozygous call
        				if (normCalls == null) {
        					normCalls = new ArrayList<String>();
        				}
    					normCalls.add(bc.getBase());        				
        			} else {
        				// background... no op
        			}        			
        		}
            }        	
        }

    	// calculate VCF filters, info, fields
    	
    	List<String> alts = new ArrayList<String>();
    	
    	Map<String, Integer> altCounts = new HashMap<String, Integer>(); // AC for ALL samples
    	Map<String, Integer> altCountsFirst = new HashMap<String, Integer>(); // AC for only the first (germline or tumor) samples
    	
    	int totalDepth = 0; // AD for INFO (depth across all samples)
    	int firstDepth = 0; // AD for FORMAT tumor/first sample 
    	int altCount = 0;
    	int refCount = 0;
    	int indelCount = 0;
        
    	List<BaseCount> sorted = bt.getSorted();
    	Collections.reverse(sorted); // we want descending order here.
    	
    	String maxRef = record.refBase;
    	refCount = bt.getCount(record.refBase);

        for (BaseCount bc: sorted) {
        	if (!bc.getBase().equals(record.refBase)) {
        		if (bc.getCount()>0) {
        			if (normCalls != null && normCalls.contains(bc.getBase())) {
        				// if we have normal calls (we are in a tumor-normal workflow)
        				// and this base is in the normal GT, then skip.
        				continue;
        			}
        			
        			if (alts.size() == 0) {
        				altCount = bc.getCount(); 
        			}
        			
        			alts.add(bc.getBase());
        			altCounts.put(bc.getBase(), bc.getCount());
        			altCountsFirst.put(bc.getBase(), bc.getCount());
        			if (bc.getBase().startsWith("-")) {
        				// deletion
        				if (bc.getBase().length()>maxRef.length()) { 
        					// this includes the '-' prefix to account for the actual ref base
        					maxRef = record.refBase + bc.getBase().substring(1);
        				}
        				indelCount++;
        			} else if (bc.getBase().startsWith("+")) {
        				indelCount++;
        			}
        		}
        	}
        	totalDepth += bc.getCount();
        	firstDepth += bc.getCount();
        }
        
        if (alts.size() == 0) {
        	// if we don't have an alt-call, don't write anything.
        	return;
        }        

//    	BaseTally bt2 = null;    	
    	int secondDepth = 0;
    	int secondAltCount = 0;
    	int secondRefCount = 0;

        if (record.getNumSamples() > 1 && record.getSampleRecords(1).calls != null && record.getSampleRecords(1).calls.size()>0) {
        	// tumor-normal calling
        	bt2 = BaseTally.parsePileupRecord(record.getSampleRecords(1).calls);
        	// we will only look for alt calls present in the test sample (so, not already seen in normal as hom/het)
        	for (String alt : alts) {
        		altCounts.put(alt, altCounts.get(alt) + bt2.getCount(alt));
            	totalDepth += bt2.getCount(alt);
            	secondDepth += bt2.getCount(alt);
        	}
        	totalDepth += bt2.getCount(record.refBase);
        	secondDepth += bt2.getCount(record.refBase);
        	secondAltCount = bt2.getCount(alts.get(0));
        	secondRefCount = bt2.getCount(record.refBase);
        }
        
        
        List<String> adInfoVals = new ArrayList<String>();
        adInfoVals.add(""+(refCount+secondRefCount));
        for (String alt: alts) {
        	adInfoVals.add(""+altCounts.get(alt));
        }
        

        // If tumor/normal
        // 		process the normal FIRST to find potential germline variants (het)
        // 		Subtract those from the alts list. And perform variant calling on the rest.
        //      (this is done above)
        //
        //	    You're looking for if a variant is real (and not in normal)
        //
        //
        // If tumor/only, you're looking for if the variant is likely real
        //
        // For germline, you're looking for if a variant is real and at least het.
        //
        
        String GT1 = "./."; // tumor GT
        String GT2 = "./."; // normal GT
        List<String> filters = new ArrayList<String>();


        if (this.tumorOnly || record.getNumSamples() > 1) {
        	// somatic calling
        	List<String> gtAcc = new ArrayList<String>();
        	boolean isGood = false;
        	
//        	String s = "";
        	
            for (int i=0; i<alts.size(); i++) {
            	String alt = alts.get(i);
            	int count = altCountsFirst.get(alt);
            	
//            	s += count+",";
            	
            	double bg = StatUtils.calcPvalueBackground(count, firstDepth, this.errorRate);
            	double present = StatUtils.calcPvaluePresent(count, firstDepth);
            	
            	if (present > bg) {
            		// we want values that are larger than error
            		gtAcc.add(""+(i+1));
            		isGood = true;
            	}
            }
            
            if (gtAcc.size() == 0) {
            	GT1 = "0/0";
            } else if (gtAcc.size() == 1) {
            	GT1 = "0/"+gtAcc.get(0);
            } else if (gtAcc.size() == 2) {
            	GT1 = StringUtils.join("/", gtAcc);
            } // else keep it ./.
            

        	// tumor GT/filter
    		if (!isGood) {
    			filters.add("low_pred_call_tumor");
//    			System.out.println("Ref: " + refCount+", alts: "+ s);
//                for (int i=0; i<alts.size(); i++) {
//                	String alt = alts.get(i);
//                	int count = altCounts.get(alt);
//                	
//                	s += count+",";
//                	
//                	double bg = StatUtils.calcPvalueBackground(count, firstDepth, this.errorRate);
//                	double present = StatUtils.calcPvaluePresent(count, firstDepth);
//                	
//        			System.out.println("    " + alt + " (" + count + "/" + firstDepth+") bg: "+bg+", present:"+present);
//
//                }
            }
            
        } else {
        	// germline calls
        	
        	List<String> gtAcc = new ArrayList<String>();
        	boolean isGood = false;
        	
            for (int i=0; i<alts.size(); i++) {
            	String alt = alts.get(i);
            	int count = altCountsFirst.get(alt);
            	
    			double hom = StatUtils.calcPvalueHomozygous(count, firstDepth, this.errorRate);
    			double het = StatUtils.calcPvalueHeterozygous(count, firstDepth);
    			double bg = StatUtils.calcPvalueBackground(count, firstDepth, this.errorRate);
    			
    			if (hom > het && hom > bg) {
    				// homozygous call
            		GT1 = "" + (i+1) + "/" + (i+1);
            		isGood = true;
            		break;
    			} else if (het > hom && het > bg) {
    				// heterozygous call
            		gtAcc.add(""+(i+1));
            		isGood = true;
    			} else {
    				// background... no op
    			}
            }
            
            if (GT1.equals("./.")) {
	            if (gtAcc.size() == 0) {
	            	GT1 = "0/0";
	            } else if (gtAcc.size() == 1) {
	            	GT1 = "0/"+gtAcc.get(0);
	            } else if (gtAcc.size() == 2) {
	            	GT1 = StringUtils.join("/", gtAcc);
	            } // else keep it ./.
            }
    		if (!isGood) {
    			filters.add("low_pred_call_germline");
    		}
        }

      
        
        // calculate filters based on the FIRST alt allele only
        
        int[] depths = bt.calcAltDepth(record.refBase, alts);
        int presentVals = 0;
        for (int i=0; i<depths.length; i++) {
        	if (depths[i] > 0) {
        		presentVals += 1;
        	}
        }
        
        if (presentVals > 3) {
        	filters.add("multi_alleles");
        }
        
        if (indelCount > 1) {
        	filters.add("multi_indel");
        }
        
        if (firstDepth < this.minDepth) {
        	filters.add("min_depth");
        }
        
        if (altCount < this.minAlt) {
        	filters.add("min_abs_count");
        }
        
        if (((double)altCount / firstDepth) < this.minAF) {
        	filters.add("min_af");
        }
        
        if (record.getNumSamples() > 1 && !tumorOnly) {
        	if (secondDepth < this.minNormalDepth) {
            	filters.add("min_normal_depth");
        	}
        	if (this.maxNormalAlt > -1 && secondAltCount > this.maxNormalAlt) {
            	filters.add("max_normal_abs_count");
        	}
        	if (secondDepth > 0 && ((double)secondAltCount / secondDepth) > this.maxNormalAF) {
            	filters.add("max_normal_af");
        	}
        }
        
        

        if (passingOnly && filters.size() > 0) {
        	return;
        }
        
        
        
        writer.write(record.ref); // chrom
        writer.write(record.pos+1);
        writer.write("."); // dbsnp record
        
        
        // this is the ref value that covers the maximal deletion
        writer.write(maxRef);

        // convert the alts to be in reference to the base above (which could reflect a deletion)
        
        List<String> altRef = new ArrayList<String>();

        // split the first value (the base) from the rest (what was deleted)
        String refLeft = maxRef.substring(0,1);
        String refRight = maxRef.substring(1);
        
        for (String alt: alts) {
        	if (alt.startsWith("+")) {
        		// refbase + inserted bases + deleted bases
        		altRef.add(refLeft + alt.substring(1) +  refRight);
        	} else if (alt.startsWith("-")) {
        		// check to see if this is the longest deletion or a different one.
        		if (refRight.length() == (alt.length()-1)) {
        			// longest deletion...
        			altRef.add(refLeft);
        		} else {
        			// shorter, so add back the ending...
        			altRef.add(refLeft + refRight.substring(alt.length()-1));
        		}
        	} else {
        		// alt base + deleted bases
        		altRef.add(alt +  refRight);
        	}
        }
        
        writer.write(StringUtils.join(",", altRef));
        
        
        writer.write("."); // score
        
        if (noFilters || filters.size() == 0) {
        	writer.write("PASS"); // filter
        } else {
        	writer.write(StringUtils.join(",", filters));
        }
        
        // only write a simple INFO field
        writer.write("DP="+totalDepth+";AD="+StringUtils.join(",", adInfoVals)); // info -- could omit with '.'

        writer.write("GT:DP:AD:AF:SAC");

        List<String> recOuts = new ArrayList<String>();
        recOuts.add(GT1);
        recOuts.add(""+bt.calcDepth());
        recOuts.add(StringUtils.join(",", bt.calcAltDepth(record.refBase, alts)));
        recOuts.add(StringUtils.join(",", bt.calcAltFreq(alts)));
        recOuts.add(StringUtils.join(",", bt.calcAltStrand(record.refBase, alts)));
        
        writer.write(StringUtils.join(":", recOuts));
  
        if (record.getNumSamples()>1) {
	        if (bt2 != null && bt2.calcDepth() > 0) {
	            List<String> recOuts2 = new ArrayList<String>();
	            
	            recOuts.add(GT2);
	            recOuts2.add(""+bt2.calcDepth());        
	            recOuts2.add(StringUtils.join(",", bt2.calcAltDepth(record.refBase, alts)));
	            recOuts2.add(StringUtils.join(",", bt2.calcAltFreq(alts)));
	            recOuts2.add(StringUtils.join(",", bt2.calcAltStrand(record.refBase, alts)));
	            
	            writer.write(StringUtils.join(":", recOuts2));
	        } else {
	        	writer.write(".:.:.:.:.");
	        }
        }

        writer.eol();        
        
    }

}
