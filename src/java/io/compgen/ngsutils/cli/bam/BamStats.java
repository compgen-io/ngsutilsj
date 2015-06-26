package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringUtils;
import io.compgen.common.TallyCounts;
import io.compgen.common.TallyValues;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.annotation.AnnotatedRegionCounter;
import io.compgen.ngsutils.annotation.GenicRegion;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

@Command(name="bam-stats", desc="Stats about a BAM file and the library orientation", category="bam")
public class BamStats extends AbstractOutputCommand {
    private String filename = null;
    private String gtfFilename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean showUnmappedRef = false;

    private Map<String,TallyCounts> numTagCounts = new HashMap<String,TallyCounts>();
    private Map<String,TallyValues<String>> strTagCounts = new HashMap<String,TallyValues<String>>();
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Display reference counts even if they have no reads mapped to them", name="show-unmapped-ref")
    public void setShowUnmappedRef(boolean showUnmappedRef) {
        this.showUnmappedRef = showUnmappedRef;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(desc="GTF annotation file (note: library-orientation will be automatically determined)", name="gtf")
    public void setGTFFilename(String filename) {
        this.gtfFilename = filename;
    }    

    @Option(desc="Count tag value distribution (comma-delimited list, including type, e.g. NH:i,RG:Z)", name="tags")
    public void setTags(String tags) throws CommandArgumentException {
        for (String k: tags.split(",")) {
            String[] tag_type = k.trim().split(":");
            if (tag_type[1].toUpperCase().equals("Z")) {
                strTagCounts.put(tag_type[0], new TallyValues<String>());
            } else if (tag_type[1].toUpperCase().equals("I")) {
                numTagCounts.put(tag_type[0], new TallyCounts());
            } else {
                throw new CommandArgumentException("You must specify tags and their type (ex: NH:i)!");
            }
        }
    }
    
    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        
        AnnotatedRegionCounter geneRegionCounter = null;
        
        if (gtfFilename != null) {
            geneRegionCounter = new AnnotatedRegionCounter(gtfFilename);
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
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
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), 
            new ProgressMessage<SAMRecord>() {
                long i = 0;
                @Override
                public String msg(SAMRecord current) {
                    i++;
                    return i+" "+current.getReadName();
                }
            }, new CloseableFinalizer<SAMRecord>(){});

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

            for (String tag: strTagCounts.keySet()) {
                if (read.getAttribute(tag) != null) { 
                    strTagCounts.get(tag).incr(read.getStringAttribute(tag));
                } else {
                    strTagCounts.get(tag).incrMissing();
                }
            }

            for (String tag: numTagCounts.keySet()) {
                if (read.getAttribute(tag) != null) { 
                    numTagCounts.get(tag).incr(read.getIntegerAttribute(tag));
                } else {
                    numTagCounts.get(tag).incrMissing();
                }
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
            
            if (geneRegionCounter != null) {
                if (!read.getReadPairedFlag() || (!read.getSecondOfPairFlag() && read.getProperPairFlag() && !read.getDuplicateReadFlag() && !read.getReadFailsVendorQualityCheckFlag() && !read.getSupplementaryAlignmentFlag())) {
                    // We only profile the first read of a pair... and only proper pairs
                    geneRegionCounter.addRead(read, Orientation.FR);
                }
            }
        }
        reader.close();

        println("Total-reads:\t" + total);
        println("Mapped-reads:\t" + mapped);
        println("Unmapped-reads:\t" + unmapped);

        if (paired) {
            println();
            println("Average insert size: "+String.format("%.2f", insertSizeCounter.getMean()));
        }

        println();
        println("[Flags]");
        for (int flag:flags.keySet()) {
            if (flagCounts.get(flag) > 0) {
                println(flags.get(flag)+" (0x"+Integer.toHexString(flag)+")"+":\t"+flagCounts.get(flag));
            }
        }
        
        for (String tag: strTagCounts.keySet()) {
            println();
            println("["+tag+"]");
            strTagCounts.get(tag).write(out);
        }

        for (String tag: numTagCounts.keySet()) {
            println();
            println("["+tag+"]");
            numTagCounts.get(tag).write(out);
        }
        
        println();
        println("[References]");
        for (String ref: StringUtils.naturalSort(refCounts.keySet())) {
            if (showUnmappedRef || refCounts.get(ref) > 0) {
                println(ref+"\t"+refCounts.get(ref));
            }
        }

        println();
        if (geneRegionCounter!=null) {
            int maxlen = 0;
            for (GenicRegion reg: GenicRegion.values()) {
                if (reg.getDescription().length() > maxlen) {
                    maxlen = reg.getDescription().length();
                }
            }
            
            println("[Gene regions]");
            for (GenicRegion reg: GenicRegion.values()) {
                println(StringUtils.rfill(reg.getDescription(), maxlen)+"\t"+geneRegionCounter.getRegionCount(reg));
            }
            println();
            
            long sense = 0;
            long antisense = 0;
            for (GenicRegion reg: GenicRegion.values()) {
                if (reg.isGene) {
                    if (reg.isSense) {
                        sense += geneRegionCounter.getRegionCount(reg);
                    } else {
                        antisense += geneRegionCounter.getRegionCount(reg);
                    }
                }
            }

            long intronic;
            long exonic;
            long junction;

            // use 10-fold enrichment as a cutoff for strandedness... It should be around 50-100X.
            if (Math.log10((double) sense / antisense) > 1) {
                // FR
                intronic = geneRegionCounter.getRegionCount(GenicRegion.NC_INTRON, GenicRegion.CODING_INTRON, GenicRegion.UTR3_INTRON, GenicRegion.UTR5_INTRON);
                exonic = sense - intronic;
                
                junction = geneRegionCounter.getRegionCount(GenicRegion.JUNCTION, GenicRegion.JUNCTION_ANTI);
                println("Orientation\tFR");
            } else if (Math.log10((double) sense / antisense) < -1) {
                // RF
                intronic = geneRegionCounter.getRegionCount(GenicRegion.NC_INTRON_ANTI, GenicRegion.CODING_INTRON_ANTI, GenicRegion.UTR3_INTRON_ANTI, GenicRegion.UTR5_INTRON_ANTI);
                exonic = antisense - intronic;
                junction = geneRegionCounter.getRegionCount(GenicRegion.JUNCTION_ANTI, GenicRegion.NC_JUNCTION_ANTI);
                println("Orientation\tRF");
            }  else {
                // unstranded
                intronic = geneRegionCounter.getRegionCount(GenicRegion.NC_INTRON, GenicRegion.CODING_INTRON, GenicRegion.UTR3_INTRON, GenicRegion.UTR5_INTRON, GenicRegion.NC_INTRON_ANTI, GenicRegion.CODING_INTRON_ANTI, GenicRegion.UTR3_INTRON_ANTI, GenicRegion.UTR5_INTRON_ANTI);
                exonic = sense + antisense - intronic;
                junction = geneRegionCounter.getRegionCount(GenicRegion.JUNCTION, GenicRegion.JUNCTION_ANTI);
                println("Orientation\tunstranded");
            }
            
            println("Sense      \t" + sense);
            println("Anti-sense \t" + antisense);
            println();
            println("Exonic     \t" + exonic);
            println("Intronic   \t" + intronic);
            println();
            println("Junction   \t" + junction);
            println();

            if (antisense > 0) {
                println("Sense/anti-sense ratio     \t"+String.format("%.2f", (sense > antisense? (double) sense / antisense:  (double) -antisense / sense)));
            } else {
                println("Sense/anti-sense ratio     \t0");
            }
            if (intronic > 0) {
                println("Exonic/intronic ratio      \t"+String.format("%.2f", (double) exonic / intronic));
            } else { 
                println("Exonic/intronic ratio      \t0");
            }
            if (geneRegionCounter.getRegionCount(GenicRegion.INTERGENIC) > 0) {
                println("Exonic/genomic ratio       \t"+String.format("%.2f", (double) exonic / geneRegionCounter.getRegionCount(GenicRegion.INTERGENIC)));
            } else { 
                println("Exonic/genomic ratio       \t0");
            }
            if (junction > 0) {
                println("Non-junction/junction ratio\t"+String.format("%.2f", (double) (exonic-junction) / junction));
            } else {
                println("Non-junction/junction ratio\t0");
            }
        }
    }
    
    private void println() throws IOException {
        println("");
    }
    private void println(String s) throws IOException {
        out.write((s+"\n").getBytes());
    }
}
