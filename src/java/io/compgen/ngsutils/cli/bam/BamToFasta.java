package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.BaseTally;
import io.compgen.ngsutils.bam.support.BaseTally.BaseCount;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.fasta.FastaWriter;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCallOp;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="bam-tofasta", 
		 desc="For a BAM file, output sequence with variants collapsed in FASTA format.", 
		 category="bam"
		 )

public class BamToFasta extends AbstractOutputCommand {

//    private String pileupFilename = null;
    private String fastaFilename = null;
	private String bamFilename = null;
	private String bedFilename = null;
	private String region = null;

    private int minDepth = 1;
    private int minAlt = 0;
    private int minMinor = 0;
    private double minAltAF = 0.0;
    private int maxDepth = -11;
	private int minBaseQual = 13;
	private int minMapQ = 0;

    private int requiredFlags = 0;
    private int filterFlags = 0;
	
    private String bedOutputTemplate = null;
    
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
   
   @Option(desc="Minimum number of alt-calls to export (otherwise write reference base)", name="min-alt")
   public void setMinAlt(int minAlt) {
       this.minAlt = minAlt;
   }
   
   @Option(desc="Minimum minor allele frequency (otherwise write reference base)", name="min-af")
   public void setMinAltAF(double minAltAF) {
       this.minAltAF = minAltAF;
   }
   
   @Option(desc="Minimum depth to output (otherwise write reference base)", name="min-depth", defaultValue="1")
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
	
    @Option(desc="Reference genome FASTA file (required, faidx indexed)", name="ref", helpValue="fname", required=true)
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
    
	public BamToFasta() {
	}
	
	@Exec
	public void exec() throws Exception {
		if (bamFilename == null){
            throw new CommandArgumentException("You must specify a BAM file for input.");
		}

		if (fastaFilename == null){
            throw new CommandArgumentException("You must specify a reference FASTA file for input.");
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
        FastaWriter writer=null;

        
        if (region != null) {
            writer =  new FastaWriter(out);
            GenomeSpan span = GenomeSpan.parse(region);
            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, header, writer, span);
            it.close();
            writer.close();

        } else if (bedFilename != null){
			StringLineReader strReader = new StringLineReader(bedFilename);
			Set<String> chromMissingError = new HashSet<String>();
			int regionCount=0;
			
			if (bedOutputTemplate == null) {
	            writer = new FastaWriter(out);
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
                    writer = new FastaWriter(new BufferedOutputStream(new FileOutputStream(bedOutputTemplate+name+".txt")));
                }

                GenomeSpan span = new GenomeSpan(chrom, start, end);

                if (verbose) {
					System.err.println(name+" "+span);
				}
				
                CloseableIterator<PileupRecord> it = pileup.pileup(span);
	            writePileupRecords(it, header, writer, span);
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

            writer = new FastaWriter(out);
			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, header, writer, null);
			it.close();
	        writer.close();
		}
	}

    private void writePileupRecords(CloseableIterator<PileupRecord> it, SAMFileHeader header, FastaWriter writer, GenomeSpan span) throws IOException {
        String curChrom = null;
        int startPos = -1;
        String buf = "";
        
        int delUntil = 0;
        for (PileupRecord record: IterUtils.wrap(it)) {
        	if (!record.ref.equals(curChrom)) {
        		flushSeq(writer, curChrom, startPos, buf);
        		curChrom = record.ref;
        		startPos = record.pos;
        	}
        	
        	String base = "";

        	if (record.pos <= delUntil) {
        		base = "";
        	} else if (record.getSampleRecords(0).coverage < minDepth) {
        		// if we are below depth, use reference base
        		base = record.refBase.toLowerCase();
        	} else {
        		BaseTally bt = new BaseTally(); 
        		for (PileupBaseCall call: record.getSampleRecords(0).calls) {
        			if (call.op == PileupBaseCallOp.Match) {
        				bt.incr(call.call);
        			} else if (call.op == PileupBaseCallOp.Ins) {
        				bt.incr("+"+call.call);
        			} else if (call.op == PileupBaseCallOp.Del) {
        				bt.incr("-"+call.call);
        			} else if (call.op == PileupBaseCallOp.Gap) {
        				bt.incr("."); // IUPAC for a gap. 
        			} // these are the only options...
        		}
        		
        		int minCount = (int) Math.ceil(record.getSampleRecords(0).coverage * this.minAltAF);
        		if (minCount < this.minMinor) {
        			minCount = this.minMinor;
        		}
        		
        		List<BaseCount> valid = new ArrayList<BaseCount>();
        		for (BaseCount bc: bt.getSorted()) {
        			// is the base count at sufficient depth (AF and min threshold)
        			// don't export deletions ever.
    				if (bc.getCount() >= minCount) {
    					valid.add(bc);
    				}
        		}

        		if (valid.size() == 0) {
            		// No valid options... use the reference base
            		base = record.refBase.toLowerCase();
        		} else if (valid.size() == 1) {
        			// only one thing, so let's use that...	
        			BaseCount bc = valid.get(0);
        			if (bc.getBase().startsWith("+")) {
        				// insertion
        				base = bc.getBase().substring(1).toUpperCase(); 
        			} else if (bc.getBase().startsWith("-")) {
        				// deletion...
        				base = "";
        				delUntil = record.pos + bc.getBase().substring(1).length();
        			} else {
        				// gap or base... so use that.
        				base = bc.getBase().toUpperCase();
        			}
        		} else {
        			// figure out what to write. 
        			// because we have multiples, we can't include indels... FASTA just doesn't support it.

        			String validBases = "";
        			for (BaseCount bc: valid) {
        				if (bc.getBase().startsWith("+") || bc.getBase().startsWith("-")) {
        					continue;
        				}
        				validBases = validBases + bc.getBase().toUpperCase();
        			}
        			
        			base = SeqUtils.ambiguousNucleotide(validBases);
        		}
        		
        	}
        	buf += base;
        }

        flushSeq(writer, curChrom, startPos, buf);
    }

	private void flushSeq(FastaWriter writer, String curChrom, int startPos, String buf) throws IOException {
		if (curChrom != null && buf.length() > 0) {
			writer.start(curChrom+":"+(startPos+1) + "-" + (startPos + buf.length()));
			writer.seq(buf);
		}
		
	}
}
