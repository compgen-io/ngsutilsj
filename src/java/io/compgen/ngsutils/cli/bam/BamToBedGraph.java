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
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.pileup.BAMPileup;
import io.compgen.ngsutils.pileup.PileupRecord;

@Command(name="bam-tobedgraph", 
		 desc="Calculate coverave for an aligned BAM file in BedGraph format.", 
		 category="bam"
		 )

public class BamToBedGraph extends AbstractOutputCommand {

	private String bamFilename = null;
	private String bedFilename = null;
	private String region = null;

    private int maxDepth = -11;
	private int minBaseQual = 13;
	private int minMapQ = 0;

   private boolean properPairs = false;

   @Option(desc="Only count properly-paired reads", name="paired")
   public void setProperPairs(boolean properPairs) {
       this.properPairs = properPairs;
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
    
	public BamToBedGraph() {
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

        if (region != null) {
            GenomeSpan span = GenomeSpan.parse(region);            
            CloseableIterator<PileupRecord> it = pileup.pileup(span);
            writePileupRecords(it, header, writer);
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
	            writePileupRecords(it, header, writer);
				it.close();
			}
			strReader.close();
		} else {
		    // output everything

			CloseableIterator<PileupRecord> it = pileup.pileup();
            writePileupRecords(it, header, writer);
			it.close();
		}
		writer.close();
	}

    private void writePileupRecords(CloseableIterator<PileupRecord> it, SAMFileHeader header, TabWriter writer) throws IOException {
        String lastChrom = null;
        int curStart = -1;
        int curCount = -1;
        int lastPos = -1;
        int counter = 0;

        for (PileupRecord record: IterUtils.wrap(it)) {
            if (lastChrom == null || !record.ref.equals(lastChrom)) {
                if (lastChrom != null) {
                    writeLine(writer, lastChrom, curStart, lastPos+1, curCount);
                }
                lastChrom = record.ref;
                curStart = -1;
                curCount = -1;
                lastPos = -1;
            }            
            
            int count = record.getSampleRecords(0).coverage;
            if (curCount != count || record.pos != lastPos + 1) {
                if (curCount > 0) {
                    writeLine(writer, lastChrom, curStart, lastPos+1, curCount);
                }
                curStart = record.pos;
                curCount = count;
            }
            
            lastPos = record.pos;
            if (verbose && ++counter >= 100000) {
                System.err.println(record.ref+":"+record.pos);
                counter = 0;
            }
        }
        if (curCount > 0) {
            writeLine(writer, lastChrom, curStart, lastPos+1, curCount);
        }
    }

    private void writeLine(TabWriter writer, String lastChrom, int curStart, int lastPos, int curCount) throws IOException {
        writer.write(lastChrom, ""+curStart, ""+lastPos, ""+curCount);
        writer.eol();
    }

}
