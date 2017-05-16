package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
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
    private int maxDepth = -11;
	private int minBaseQual = 13;
	private int minMapQ = 0;

   private boolean properPairs = false;

   @Option(desc="Only count properly-paired reads", name="paired")
   public void setProperPairs(boolean properPairs) {
       this.properPairs = properPairs;
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
		
		TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
		writer.write_line("## input: " + bamFilename);
		if (bedFilename != null) {
	          writer.write_line("## bed-regions: " + bedFilename);
		} else if (region != null) {
	          writer.write_line("## region: " + region);
		}
				
        writer.write("chrom", "pos", "ref", "A", "C", "G", "T", "N", "plus-strand", "minus-strand");
        writer.eol();

        SamReader bam = SamReaderFactory.makeDefault().open(new File(bamFilename));
        final SAMFileHeader header = bam.getFileHeader();
        
        BAMPileup pileup = new BAMPileup(bamFilename);
        pileup.setDisableBAQ(true);
        pileup.setExtendedBAQ(false);
        pileup.setFlagRequired(properPairs ? 0x2:0);
        pileup.setMinBaseQual(minBaseQual);
        pileup.setMinMappingQual(minMapQ);
        pileup.setMaxDepth(maxDepth);
        pileup.setVerbose(verbose);
        if (fastaFilename != null) {
            pileup.setRefFilename(fastaFilename);
        }

        if (region != null) {
            GenomeSpan span = GenomeSpan.parse(region);
            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, header, writer, span);
            it.close();
        } else if (bedFilename != null){
			StringLineReader strReader = new StringLineReader(bedFilename);
			Set<String> chromMissingError = new HashSet<String>();
			int regionCount=0;
			for (String line: strReader) {
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
	            writePileupRecords(it, header, writer, span);
				it.close();
			}
			strReader.close();
		} else {
		    // output everything

			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, header, writer, null);
			it.close();
		}
		writer.close();
	}

    private void writePileupRecords(CloseableIterator<PileupRecord> it, SAMFileHeader header, TabWriter writer, GenomeSpan span) throws IOException {
        String lastChrom = null;
        int lastPos = -1;
        int counter = 0;

        // TODO: Pad out a span too! 
        
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
                                        System.err.println("Flushing " + span);
                                    } else {
                                        System.err.println("Flushing " + lastChrom);
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
            
            if (record.getSampleCount(0) >= minDepth) {
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
            int maxLength = header.getSequence(lastChrom).getSequenceLength();
            if (span != null) {
                maxLength = span.end;
            }

            while (lastPos+1 < maxLength) {
                if (verbose && !debugWritten) {
                    debugWritten = true;
                    if (span != null) {
                        System.err.println("Flushing " + span);
                    } else {
                        System.err.println("Flushing " + lastChrom);
                    }
                }
                writeEmptyRecord(lastChrom, ++lastPos, writer);
            }
        }
    }

    private void writeRecord(PileupRecord record, TabWriter writer) throws IOException {
        writer.write(record.ref);
        writer.write(record.pos+1);
        writer.write(record.refBase);
        
        int a = 0;
        int c = 0;
        int g = 0;
        int t = 0;
        int n = 0;
        int pos = 0;
        int neg = 0;
        
        for (PileupBaseCall call: record.getSampleRecords(0).calls) {
            switch(call.call) {
            case "A":
                a++;
                break;
            case "C":
                c++;
                break;
            case "G":
                g++;
                break;
            case "T":
                t++;
                break;
            default:
                n++;
            }
            
            if (call.plusStrand) {
                pos++;
            } else {
                neg++;
            }
        }
        
        writer.write(a);
        writer.write(c);
        writer.write(g);
        writer.write(t);
        writer.write(n);
        writer.write(pos);
        writer.write(neg);
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
        writer.eol();        
        
    }

}
