package org.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceRecord;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.AnnotatedRegionCounter;
import org.ngsutils.support.NaturalSort;
import org.ngsutils.support.TallyCounts;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-stats")
@Command(name="bam-stats", desc="Stats about a BAM file and the library orientation", cat="bam", experimental=true)
public class BamStats extends AbstractOutputCommand {
    private String filename = null;
    private String gtfFilename = null;
    private boolean lenient = false;
    private boolean silent = false;

    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(description = "GTF annotation file", longName="gtf", defaultToNull=true)
    public void setGTFFilename(String filename) {
        this.gtfFilename = filename;
    }    

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input BAM filename!");
        }
        
        AnnotatedRegionCounter counter = null;
        
        if (gtfFilename != null) {
            counter = new AnnotatedRegionCounter(gtfFilename);
        }
        
        SAMFileReader reader;
        FileChannel channel = null;
        String name;
        if (filename.equals("-")) {
            reader = new SAMFileReader(System.in);
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = new SAMFileReader(fis);
            name = f.getName();
        }
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        long total = 0;
        long mapped = 0;
        long unmapped = 0;
        
        Map<Integer, String> flags = new TreeMap<Integer, String>();
        flags.put(0x1  , "Multiple fragments");
        flags.put(0x2  , "All fragments aligned");
        flags.put(0x4  , "Unmapped");
        flags.put(0x8  , "Next unmapped");
        flags.put(0x10 , "Reverse complimented");
        flags.put(0x20 , "Next reverse complimented");
        flags.put(0x40 , "First fragment");
        flags.put(0x80 , "Last fragment");
        flags.put(0x100, "Secondary alignment");
        flags.put(0x200, "QC Fail");
        flags.put(0x400, "PCR/Optical duplicate");
        flags.put(0x800, "Supplementary");

        Map<Integer, Integer> flagCounts = new HashMap<Integer, Integer>();
        flagCounts.put(0x1  , 0);
        flagCounts.put(0x2  , 0);
        flagCounts.put(0x4  , 0);
        flagCounts.put(0x8  , 0);
        flagCounts.put(0x10 , 0);
        flagCounts.put(0x20 , 0);
        flagCounts.put(0x40 , 0);
        flagCounts.put(0x80 , 0);
        flagCounts.put(0x100, 0);
        flagCounts.put(0x200, 0);
        flagCounts.put(0x400, 0);
        flagCounts.put(0x800, 0);
        
        Map<String, Integer> refCounts = new HashMap<String, Integer>();
        for (SAMSequenceRecord ref: reader.getFileHeader().getSequenceDictionary().getSequences()) {
            refCounts.put(ref.getSequenceName(), 0);
        }
        
        TallyCounts insertSizeCounter = new TallyCounts();
        boolean paired = false;
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }});;
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.getReadPairedFlag() && read.getSecondOfPairFlag()) {
                // We only profile the first read of a pair...
                continue;
            }
            
            total++;

            for (int flag: flags.keySet()) {
                if ((read.getFlags() & flag) > 0) {
                    flagCounts.put(flag, flagCounts.get(flag) + 1);
                }
            }
            
            if (!read.getReadUnmappedFlag()) {
                mapped++;
            } else {
                unmapped++;
                continue;                
            }

            if (read.getReadPairedFlag()) {
                paired = true; 
                if (read.getProperPairFlag() && read.getReferenceIndex().equals(read.getMateReferenceIndex())) {
                    if (read.getInferredInsertSize() < 2000) {
                        // limit to something reasonable - RNAseq can skew this horribly.
                        insertSizeCounter.incr(Math.abs(read.getInferredInsertSize()));
                    }
                }
            }
            
            refCounts.put(read.getReferenceName(), refCounts.get(read.getReferenceName())+1);
            
            if (counter != null) {
                if (!read.getReadPairedFlag() || (!read.getSecondOfPairFlag() && read.getProperPairFlag() && !read.getDuplicateReadFlag() && !read.getReadFailsVendorQualityCheckFlag())) {
                    // We only profile the first read of a pair... and only proper pairs
                    counter.addRead(read);
                    
                }
            }
        }
        reader.close();
        System.out.println("Total-reads:\t" + total);
        System.out.println("Mapped-reads:\t" + mapped);
        System.out.println("Unmapped-reads:\t" + unmapped);
        System.out.println();
        if (paired) {
            System.out.println("Average insert size: "+String.format("%.2f", insertSizeCounter.getMean()));
            System.out.println();
        }
        System.out.println("[Flags]");
        for (int flag:flags.keySet()) {
            if (flagCounts.get(flag) > 0) {
                System.out.println(flags.get(flag)+" (0x"+Integer.toHexString(flag)+")"+":\t"+flagCounts.get(flag));
            }
        }
        System.out.println();
        System.out.println("[References]");
        for (String ref: NaturalSort.naturalSort(refCounts.keySet())) {
            System.out.println(ref+":\t"+refCounts.get(ref));
        }
        System.out.println();
        if (counter!=null) {
            System.out.println("[Gene regions]");
            System.out.println("Coding        :\t"+counter.getCoding());
            System.out.println("Coding-rev    :\t"+counter.getCodingRev());
            System.out.println("Junction      :\t"+counter.getJunction());
            System.out.println("Junction-rev  :\t"+counter.getJunctionRev());
            System.out.println("Other-exon    :\t"+counter.getOtherExon());
            System.out.println("Other-exon-rev:\t"+counter.getOtherExonRev());
            System.out.println("5'UTR         :\t"+counter.getUtr5());
            System.out.println("5'UTR-rev     :\t"+counter.getUtr5Rev());
            System.out.println("3'UTR         :\t"+counter.getUtr3());
            System.out.println("3'UTR-rev     :\t"+counter.getUtr3Rev());
            System.out.println("Intron        :\t"+counter.getIntron());
            System.out.println("Intron-rev    :\t"+counter.getIntronRev());
            System.out.println("Mitochondrial :\t"+counter.getMitochrondrial());
            System.out.println("Intergenic    :\t"+counter.getIntergenic());
            System.out.println();
            long sense = counter.getCoding() +
                    counter.getJunction() +
                    counter.getOtherExon() +
                    counter.getUtr5() +
                    counter.getUtr3() +
                    counter.getIntron();

            long antisense = counter.getCodingRev() +
                    counter.getJunctionRev() +
                    counter.getOtherExonRev() +
                    counter.getUtr5Rev() +
                    counter.getUtr3Rev() +
                    counter.getIntronRev();
            
            long intronic;
            long exonic;
            long junction;

            // use 10-fold enrichment as a cutoff for strandedness... It should be around 100X.
            if (Math.log10((double) sense / antisense) > 1) {
                // FR
                intronic = counter.getIntron();
                exonic = sense - intronic;
                
                junction = counter.getJunction();
                System.out.println("Orientation:\tFR");
            } else if (Math.log10((double) sense / antisense) < 1) {
                // RF
                intronic = counter.getIntronRev();
                exonic = antisense - intronic;
                junction = counter.getJunctionRev();
                System.out.println("Orientation:\tRF");
            }  else {
                // unstranded
                intronic = counter.getIntron() + counter.getIntronRev();
                exonic = sense + antisense - intronic;
                junction = counter.getJunction() + counter.getJunctionRev();
                System.out.println("Orientation:\tunstranded");
            }
            
            System.out.println("Sense     :\t" + sense);
            System.out.println("Anti-sense:\t" + antisense);
            System.out.println();
            System.out.println("Exonic    :\t" + exonic);
            System.out.println("Intronic  :\t" + intronic);
            System.out.println();
            System.out.println("Junction  :\t" + junction);
            System.out.println();

            if (antisense > 0) {
                System.out.println("Sense/anti-sense ratio     :\t"+String.format("%.2f", (sense > antisense? (double) sense / antisense:  (double) -antisense / sense)));
            } else {
                System.out.println("Sense/anti-sense ratio     :\t0");
            }
            if (intronic > 0) {
                System.out.println("Exonic/intronic ratio      :\t"+String.format("%.2f", (double) exonic / intronic));
            } else { 
                System.out.println("Exonic/intronic ratio      :\t0");
            }
            if (counter.getIntergenic() > 0) {
                System.out.println("Exonic/genomic ratio       :\t"+String.format("%.2f", (double) exonic / counter.getIntergenic()));
            } else { 
                System.out.println("Exonic/genomic ratio       :\t0");
            }
            if (junction > 0) {
                System.out.println("Non-junction/junction ratio:\t"+String.format("%.2f", (double) (exonic-junction) / junction));
            } else {
                System.out.println("Non-junction/junction ratio:\t0");
            }

        }
    }
}
