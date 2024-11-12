package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.support.BaseTally;
import io.compgen.ngsutils.bam.support.BaseTally.BaseCount;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;
import io.compgen.ngsutils.pileup.PileupRecord.PileupBaseCall;

@Command(name="bam-basecall", 
		 desc="For a BAM file, output the basecalls (ACGTN) at each genomic position.", 
		 category="bam", 
		 doc=      "This command produces a text file that is significantly easier to parse than a \n"
		         + "similar pileup file. Base positions are reported as 1-based values."
		 )

public class BamBaseCall extends AbstractOutputCommand {

//    private String pileupFilename = null;
    private String fastaFilename = null;
	private String bamFilename = null;
	private String bedFilename = null;
	private String region = null;

    private int minDepth = 1;
    private int minAlt = 0;
    private int minMinor = 0;
    private int maxDepth = -11;
	private int minBaseQual = 13;
	private int minMapQ = 0;

    private int requiredFlags = 0;
    private int filterFlags = 0;
	
    private boolean exportBAF = false;
    private boolean exportIndel = false;
    private boolean exportDepth = false;
    
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
   
   @Option(desc="Minimum number of non-reference (alt) calls (set to > 0 to export only alt bases)", name="min-alt")
   public void setMinAlt(int minAlt) {
       this.minAlt = minAlt;
   }
   
   @Option(desc="Minimum number of minor-allele calls (set to avoid exporting homozygous non-ref bases)", name="min-minor")
   public void setMinMinor(int minMinor) {
       this.minMinor = minMinor;
   }
   
   @Option(desc="Export Indel counts", name="indel")
   public void setExportIndel(boolean exportIndel) {
       this.exportIndel = exportIndel;
   }
   
   @Option(desc="Export BAF (B-allele frequency)", name="baf")
   public void setExportBAF(boolean exportBAF) {
       this.exportBAF = exportBAF;
   }
   
   @Option(desc="Export total depth", name="depth")
   public void setDepth(boolean exportDepth) {
       this.exportDepth = exportDepth;
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
    
	public BamBaseCall() {
	}
	
	@Exec
	public void exec() throws Exception {
		if (bamFilename == null){
            throw new CommandArgumentException("You must specify a BAM file for input.");
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
        
        if (exportBAF && fastaFilename==null) {
            throw new CommandArgumentException("You must set --fasta when --baf is also set.");
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
        TabWriter writer=null;

        
        if (region != null) {
            writer = setupWriter(out, pileup);
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
	            writer = setupWriter(out, pileup);
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
                    writer = setupWriter(new BufferedOutputStream(new FileOutputStream(bedOutputTemplate+name+".txt")), pileup);
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

            writer = setupWriter(out, pileup);
			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, header, writer, null);
			it.close();
	        writer.close();
		}
	}

    private TabWriter setupWriter(OutputStream out, BAMPileup pileup) throws IOException {
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + bamFilename);
        if (bedFilename != null) {
              writer.write_line("## bed-regions: " + bedFilename);
        } else if (region != null) {
              writer.write_line("## region: " + region);
        }

        writer.write("## pileup-cmd: "+StringUtils.join(" ", pileup.getCommand()));
        writer.eol();

        writer.write("chrom", "pos", "ref", "A", "C", "G", "T", "N");
        if (exportIndel) {
            writer.write("ins", "del");
        }
        writer.write("plus-strand", "minus-strand");
        if (exportBAF) {
            writer.write("BAF");
        }
        if (exportDepth) {
            writer.write("DP");
        }
        writer.eol();
        return writer;
    }

    private void writePileupRecords(CloseableIterator<PileupRecord> it, SAMFileHeader header, TabWriter writer, GenomeSpan span) throws IOException {
        String lastChrom = null;
        int lastPos = -1;
        int counter = 0;

        // TODO: Pad out a span too! Done?
        
        for (PileupRecord record: IterUtils.wrap(it)) {
            if (minDepth == 0) {
                if (lastChrom == null || !lastChrom.equals(record.ref)) {
                    // close out the end of the chromosome/span
                    if (lastChrom!=null) {
                        boolean debugWritten = false;
                        int maxLength = header.getSequence(lastChrom).getSequenceLength();
                        if (span != null) {
                            maxLength = span.end;
                        }

                        while (lastPos+1 < maxLength) {
                            if (verbose) {
                                if (verbose && !debugWritten) {
                                    debugWritten = true;
                                    if (span != null) {
                                        System.err.println("Flushing " + span + " /1");
                                    } else {
                                        System.err.println("Flushing " + lastChrom + " /2");
                                    }
                                }
                            }
                            counter++;
                            writeEmptyRecord(lastChrom, ++lastPos, writer);
                        }
                    }
                    lastChrom = record.ref;
                    lastPos = -1;
                    if (span != null) {
                        lastPos = span.start-1;
                    }
                    counter = 0;
                    if (verbose) {
                        System.err.println(record.ref);
                    }
                }

                boolean debugWritten = false;
                while ((lastPos+1) < record.pos) {
                    if (verbose && !debugWritten) {
                        debugWritten = true;
                        System.err.println("Filling in " + lastChrom+":"+(lastPos+2)+"-"+(record.pos+1));
                    }
                    counter++;
                    writeEmptyRecord(record.ref, ++lastPos, writer);
                }
            }
            
            if (minDepth == 0 || (record.getSampleRecords(0).calls!=null && record.getSampleRecords(0).calls.size() >= minDepth)) {
                writeRecord(record, writer);
            }

            counter++;
            lastPos = record.pos;
            if (verbose && counter >= 100000) {
                System.err.println(record.ref+":"+record.pos);
                counter = 0;
            }
        }
        if (minDepth == 0) {
            boolean debugWritten = false;
            if (lastChrom!=null || span != null) {
                int maxLength=-1;
                if (lastChrom != null && header.getSequence(lastChrom)!= null) {
                    maxLength = header.getSequence(lastChrom).getSequenceLength();
                }
                if (span != null) {
                    maxLength = span.end;
                    if (lastChrom == null) {
                        lastChrom = span.ref;
                        lastPos = span.start-1;
                    }
                }
    
                while (lastPos+1 < maxLength) {
                    if (verbose && !debugWritten) {
                        debugWritten = true;
                        if (span != null) {
                            System.err.println("Flushing " + span + " /3");
                        } else {
                            System.err.println("Flushing " + lastChrom + " /4");
                        }
                    }
                    writeEmptyRecord(lastChrom, ++lastPos, writer);
                }
            } else {
                System.err.println("WARNING: 101 - lastChrom == null?");
            }
        }
    }

    private void writeRecord(PileupRecord record, TabWriter writer) throws IOException {

        BaseTally bt = new BaseTally("A", "C", "G", "T", "Ins", "Del");
        
        int n = 0;
        int pos = 0;
        int neg = 0;
        
        for (PileupBaseCall call: record.getSampleRecords(0).calls) {
            if (call.op == PileupRecord.PileupBaseCallOp.Match) {
                if (bt.contains(call.call)) {
                    bt.incr(call.call, call.plusStrand);
                } else {
                    n++;
                }
            } else if (call.op == PileupRecord.PileupBaseCallOp.Ins){
                bt.incr("Ins", call.plusStrand);
            } else if (call.op == PileupRecord.PileupBaseCallOp.Del){
                bt.incr("Del", call.plusStrand);
            }

            if (call.plusStrand) {
                pos++;
            } else {
                neg++;
            }
        }

        int alt = bt.nonRefSum(record.refBase);
        int total = bt.sum();
        
        if (alt < minAlt) {
            return;
        }

        List<BaseCount> calls = bt.getSorted();
        
        if (minMinor > 0) {
            if (calls.get(calls.size()-2).getCount() < minMinor) {
                return;
            }
        }
        
        writer.write(record.ref);
        writer.write(record.pos+1);
        writer.write(record.refBase);
        writer.write(bt.getCount("A"));
        writer.write(bt.getCount("C"));
        writer.write(bt.getCount("G"));
        writer.write(bt.getCount("T"));
        writer.write(n);
        if (exportIndel) {
            writer.write(bt.getCount("Ins"));
            writer.write(bt.getCount("Del"));
        }
        writer.write(pos);
        writer.write(neg);
        if (exportBAF) {
            double b;
            if (calls.get(calls.size()-1).getBase().equals(record.refBase)) {
                // if the last one (greatest count) is ref, b-allele is the second best.
                b = calls.get(calls.size()-2).getCount();
            } else {
                // if the last one (greatest count) isn't ref, so b-allele is that one
                b = calls.get(calls.size()-1).getCount();
            }
            writer.write(b / total);
        }
        if (exportDepth) {
        	writer.write(total);
        }
        writer.eol();        
        
    }

    private void writeEmptyRecord(String chrom, int pos, TabWriter writer) throws IOException {
        writer.write(chrom);
        writer.write(pos+1);
        writer.write("");
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        writer.write(0);
        if (exportBAF) {
            writer.write(0);
        }
        writer.eol();        
        
    }

}
