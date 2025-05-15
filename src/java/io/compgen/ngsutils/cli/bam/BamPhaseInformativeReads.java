package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;

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
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressStats;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupSampleRecord;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;

@Command(name="bam-pir", 
		 desc="For a BAM file, extract the phase-informative reads", 
		 category="bam" ,
		 experimental=true
		 )

public class BamPhaseInformativeReads extends AbstractOutputCommand {

//    private String pileupFilename = null;
    private String fastaFilename = null;
	private String[] bamFilenames = null;
	private String bedFilename = null;
	private String region = null;
	private String vcfFilename = null;

    private int minDepth = 1;
    private int minAlt = 0;
    private int minMinor = 0;
    private int maxDepth = -11;
	private int minBaseQual = 13;
	private int minMapQ = 0;

    private int requiredFlags = 0;
    private int filterFlags = 0;
    
    private boolean onlySNVs = false;
    private boolean onlyPassing = false;
    private boolean onlyPresent = false;
    private boolean onlyHet = false;
    private boolean vcfGT = false;
    
    private String bedOutputTemplate = null;
    
    @Option(desc = "Only calculate SNVs (no indels)", name = "only-snvs")
    public void setOnlySNV(boolean val) {
    	onlySNVs = val;
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
    
   @Option(desc="If using a BED file, write the output to a separate file for each BED region", name="bed-output", helpValue="template.txt")
   public void setBedOutputTemplate(String bedOutputTemplate) {
       this.bedOutputTemplate = bedOutputTemplate;
   }
   
   @Option(desc="Minimum number of non-reference (alt) calls (set to > 0 to export only alt bases)", name="min-alt")
   public void setMinAlt(int minAlt) {
       this.minAlt = minAlt;
   }
   
   @Option(desc="Minimum number of minor-allele calls (set to avoid exporting homozygous non-ref bases)", name="min-minor")
   public void setMinMinor(int minMinor) {
       this.minMinor = minMinor;
   }
   
   @Option(desc="Minimum depth to output (set to 0 to output all bases)", name="min-depth", defaultValue="1")
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
	
    @Option(desc="Reference genome FASTA file (optional, faidx indexed)", name="ref", helpValue="fname")
    public void setFASTAFilename(String filename) {
        this.fastaFilename = filename;
    }

    @UnnamedArg(name = "FILE...")
    public void setFilename(String[] filename) {
        this.bamFilenames = filename;
    }

    @Option(desc="Regions BED file (output only bases within these regions)", name="bed", helpValue="fname")
    public void setBEDFilename(String filename) {
    	this.bedFilename = filename;
    }
    @Option(desc="Region to report base calls (chr:start-end)", name="region")
    public void setRegion(String region) {
    	this.region = region;
    }

    @Option(desc="Find variants in this VCF file", name="vcf")
    public void setVCFFilename(String vcfFilename) {
    	this.vcfFilename = vcfFilename;
    }
    @Option(desc = "Only include passing variants (req --vcf)", name = "vcf-passing")
    public void setOnlyPassing(boolean val) {
    	this.onlyPassing = val;
    }

    @Option(desc = "Only include variants with a heterozygous GT call (req --vcf, implies --vcf-present)", name = "vcf-het")
    public void setOnlyHet(boolean val) {
    	this.onlyHet = val;
    	this.onlyPresent = val;
    }
    @Option(desc = "Only include variants present in the VCF file (req --vcf)", name = "vcf-present")
    public void setOnlyPresent(boolean val) {
    	onlyPresent = val;
    }
    @Option(desc = "Include the GT value from the VCF record (first sample listed, req --vcf)", name = "vcf-gt")
    public void setVCFGT(boolean val) {
    	vcfGT = val;
    }


	@Exec
	public void exec() throws Exception {
		if (bamFilenames == null || bamFilenames.length==0){
            throw new CommandArgumentException("You must specify at least one BAM file for input.");
		}

        if (fastaFilename == null) {
            throw new CommandArgumentException("You must specify a reference FASTA file.");
        }
		
        if (bedFilename != null && region != null) {
            throw new CommandArgumentException("You can not specify both --region and --bed.");
        }

        if (minMinor>0 && minDepth == 0) {
            throw new CommandArgumentException("You must set --min-depth > 0 when --min-minor is > 0");
        }

        if (minAlt>0 && minDepth == 0) {
            throw new CommandArgumentException("You must set --min-depth > 0 when --min-alt is > 0");
        }

        if (minAlt>0 && fastaFilename==null) {
            throw new CommandArgumentException("You must set --fasta when --min-alt is > 0.");
        }
        
        if (onlyPassing && vcfFilename==null) {
            throw new CommandArgumentException("You must set --vcf with --vcf-passing");
        }
        
        if (onlyHet && vcfFilename==null) {
            throw new CommandArgumentException("You must set --vcf with --vcf-het");
        }
        
        if (onlyPresent && vcfFilename==null) {
            throw new CommandArgumentException("You must set --vcf with --vcf-present");
        }
        
        if (vcfGT && vcfFilename==null) {
            throw new CommandArgumentException("You must set --vcf with --vcf-gt");
        }
        
        if (vcfFilename!=null && vcfFilename.equals("-")) {
            throw new CommandArgumentException("--vcf must be an indexed VCF file");
        }

        
        SamReader bam = SamReaderFactory.makeDefault().open(new File(bamFilenames[0]));
        final SAMFileHeader header = bam.getFileHeader();
        
        BAMPileup pileup = new BAMPileup(bamFilenames);
        pileup.setDisableBAQ(true);
        pileup.setExtendedBAQ(false);
        pileup.setQname(true);
        pileup.setFlagRequired(requiredFlags);
        pileup.setFlagFilter(filterFlags);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMinMappingQual(minMapQ);
        pileup.setMaxDepth(maxDepth);
//        pileup.setVerbose(verbose);
        if (fastaFilename != null) {
            pileup.setRefFilename(fastaFilename);
        }
        TabWriter writer=null;

        VCFReader vcfFile = null;
        if (vcfFilename != null) {
        	if (verbose) {
        		System.err.println("Reading variants from: " + vcfFilename);
        	}
            vcfFile = new VCFReader(vcfFilename);
        }
        
        if (region != null) {
            writer = setupWriter(out, pileup);
            GenomeSpan span = GenomeSpan.parse(region);
            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, writer, span.length(), 0, vcfFile);
            it.close();
            writer.close();

        } else if (bedFilename != null){
        	long total = 0;
			StringLineReader strReader1 = new StringLineReader(bedFilename);
			for (String line: strReader1) {
			    if (line.startsWith("#") || line.trim().length()==0) {
			        continue;
			    }
				String[] cols = StringUtils.strip(line).split("\t");
				int start = Integer.parseInt(cols[1]);
				int end = Integer.parseInt(cols[2]);
				
				total += (end - start);
			}        	
			strReader1.close();

			
			StringLineReader strReader = new StringLineReader(bedFilename);
			Set<String> chromMissingError = new HashSet<String>();
			int regionCount=0;
			
			if (bedOutputTemplate == null) {
	            writer = setupWriter(out, pileup);
			}
			
			long runningLength = 0;
			
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
                    writer = setupWriter(new BufferedOutputStream(new FileOutputStream(bedOutputTemplate+name+".txt")), pileup);
                }

                GenomeSpan span = new GenomeSpan(chrom, start, end);

                if (verbose) {
					System.err.println(name+" "+span);
				}
				
                CloseableIterator<PileupRecord> it = pileup.pileup(span);
	            writePileupRecords(it, writer, total, runningLength, vcfFile);
				it.close();
	            runningLength += (end-start);

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
			long total = 0;
			for (int i=0; i<header.getSequenceDictionary().size(); i++) {
				total += header.getSequence(i).getSequenceLength();
			}

            writer = setupWriter(out, pileup);
			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, writer, total ,0, vcfFile);
			it.close();
	        writer.close();
		}
	}

    private TabWriter setupWriter(OutputStream out, BAMPileup pileup) throws IOException {
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + StringUtils.join(",", bamFilenames));
        if (bedFilename != null) {
            writer.write_line("## bed-regions: " + bedFilename);
        } else if (region != null) {
              writer.write_line("## region: " + region);
        }
        if (vcfFilename != null) {
            writer.write_line("## vcf-file: " + vcfFilename);
            if (onlyPassing) {
            	writer.write_line("## vcf-only-passing");
            }
            if (onlyHet) {
            	writer.write_line("## vcf-only-het");
            }
        }
        if (onlySNVs) {
        	writer.write_line("## only-snvs");
        }

        writer.write("## pileup-cmd: "+StringUtils.join(" ", pileup.getCommand()));
        writer.eol();

        writer.write("#chrom", "pos", "ref", "alt");
        if (vcfGT) {
        	writer.write("GT");
        }
        for (String fname: bamFilenames) {
        	writer.write(new File(fname).getName()+" refcount");
        	writer.write(new File(fname).getName()+" altcount");
        	writer.write(new File(fname).getName()+" refreads");
        	writer.write(new File(fname).getName()+" altreads");
        }
        writer.eol();
        return writer;
    }

    private void writePileupRecords(CloseableIterator<PileupRecord> it, TabWriter writer, long totalSize, long initialCount, VCFReader vcfFile) throws IOException, DataFormatException, VCFParseException {
    	final long[] counter = new long[] {initialCount};
    	int lastpos = 0;
    	String lastref = null;
    	
    	final long[] foundVariants = new long[] { 0 };

        for (PileupRecord record: IterUtils.wrap(ProgressUtils.getIterator("", it, new ProgressStats() {

    			@Override
    			public long size() {
    				return totalSize;
    			}

    			@Override
    			public long position() {
    				return counter[0];
    			}}, new ProgressMessage<PileupRecord>() {

    			@Override
    			public String msg(PileupRecord record) {
                    return record.ref+":"+record.pos + " (" + foundVariants[0] +")";
    			}}))) {
            if (lastref == null || !lastref.equals(record.ref)) {
            	lastpos = 0;
            }
            
            counter[0] = counter[0] + (record.pos - lastpos);
            lastpos = record.pos;
            lastref = record.ref;
	    	Map<String, List<Set<String>>> variantReads = new HashMap<String, List<Set<String>>>();
	    	Map<String, String> variantGT = new HashMap<String, String>();
	    	Set<String> skipRec = new HashSet<String>();
	    	for(int i=0; i<record.getNumSamples(); i++) {
	    		PileupSampleRecord rec = record.getSampleRecords(i);
	    		for (int j=0; j<rec.calls.size(); j++) {
	    			String variant;
	    			String gtVal = "";
	    			if (rec.calls.get(j).call.equals(record.refBase)) {
	    				variant = record.ref+":"+record.pos+":"+record.refBase+":"+record.refBase;
	    			} else {
	    				variant = record.ref+":"+record.pos+":"+record.refBase+":"+rec.calls.get(j).call;

	    				if (skipRec.contains(variant)) {
	    					continue;
	    				}
	    				
	    				if (!variantReads.containsKey(variant)) {
//		    				System.err.println("Looking for variant: " + variant);
		    				if (onlySNVs) {
		    					if (record.refBase.length()!=1 || rec.calls.get(j).call.length() != 1) {
//		    						System.err.println("REJECT: Not an SNV");
		    						continue;
		    					}
		    				}
		    				
		    				boolean good = true;
		    				if (vcfFile != null) {
								if (onlyPresent) {
									good = false;
								}
		    					
		    					VCFRecord vcfrec = vcfFile.getVariant(record.ref, record.pos+1, record.refBase, rec.calls.get(j).call);
		    					if (vcfrec != null) {
									if (onlyPresent) {
//			    						System.err.println("Found in VCF!");
										good = true;
									}
									
		    						if (onlyPassing && vcfrec.isFiltered()) {
//			    						System.err.println("REJECT: Filtered in VCF");
		    							good = false;
		    						}
		    						
		    						boolean isHet = false;
		    						
	    							VCFAttributeValue attr = vcfrec.getSampleAttributes().get(0).get("GT");
	    							if (attr != null) {
	    								String val = attr.toString();
	    								String[] spl = null;
	    								
	    								if (val.contains("/")) {
	    									spl = val.split("/");
	    								} else if (val.contains("|")) {
	    									spl = val.split("\\|");
	    								}
	    								
	    								if (spl != null) {
	    									gtVal = val;
	    								}
	    								
//	    								System.err.println("Found GT: " + val+ " / " + StringUtils.join(",", spl));
	    								
	    								if (spl != null && spl.length==2 && !spl[0].equals(spl[1])) {
//				    						System.err.println("Is a Het!");
	    									isHet = true;
	    								}
	    							}
	    							if (onlyHet && !isHet) {
//			    						System.err.println("REJECT: Not a het");
		    							good = false;
	    							}
		    					}	    					
		    				}
		    				
		    				if (!good) {
		    					skipRec.add(variant);
		    					continue;
		    				}
	    				}
	    			}
					String qname = rec.calls.get(j).qname;
					if (!variantReads.containsKey(variant)) {
						List<Set<String>> list = new ArrayList<Set<String>>();
						for (int k=0;k<record.getNumSamples(); k++) {
							list.add(new HashSet<String>());
						}
						variantReads.put(variant, list);
						variantGT.put(variant, gtVal);
					}
					
					variantReads.get(variant).get(i).add(qname);
				}
	    	}
	    	if (variantReads.size()>1) {
	    		foundVariants[0]++;
	    		int[] refCount = new int[record.getNumSamples()];
	    		List<Set<String>> refReads = new ArrayList<Set<String>>();
	    		
				for (int k=0;k<record.getNumSamples(); k++) {
					refCount[k] = 0;
					refReads.add(new HashSet<String>());
				}
	    		for (String variant: variantReads.keySet()) {
	    			String[] v = variant.split(":");
	    			if (v[2].equals(v[3])) {
						for (int k=0;k<record.getNumSamples(); k++) {
							refCount[k] = variantReads.get(variant).get(k).size();
							refReads.set(k, variantReads.get(variant).get(k));
						}
	    			}
	    		}
	    		for (String variant: variantReads.keySet()) {
	    			String[] v = variant.split(":");
	    			if (v[2].equals(v[3])) {
	    				continue;
	    			}
	    			writer.write(v);
	    			if (vcfGT) {
	    				writer.write(variantGT.get(variant));
	    			}
					for (int k=0;k<record.getNumSamples(); k++) {
						writer.write(refCount[k]);
						writer.write(variantReads.get(variant).get(k).size());
						writer.write(StringUtils.join(",", refReads.get(k)));
						writer.write(StringUtils.join(",", variantReads.get(variant).get(k)));
					}
					writer.eol();
	    		}
	    	}
        }
    }
}