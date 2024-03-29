package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.IncrementingStats;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.cli.bam.count.BedSpans;
import io.compgen.ngsutils.cli.bam.count.BinSpans;
import io.compgen.ngsutils.cli.bam.count.GTFSpans;
import io.compgen.ngsutils.cli.bam.count.SpanGroup;
import io.compgen.ngsutils.cli.bam.count.SpanSource;
import io.compgen.ngsutils.cli.bam.count.TabixSpans;

@Command(name="bam-count", desc="Counts the number of reads for genes (GTF), within a BED region, or by bins (--gtf, --bed, or --bins required)", category="bam")
public class BamCount extends AbstractOutputCommand {
    
    private String samFilename=null;

    private String bedFilename=null;
    private String tabixFilename=null;
    private String gtfFilename=null;

    private int binSize = 0;
    private int bedExtend = 0;
    
    private boolean contained = false;
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean proper = false;
    private boolean insert = false;
    private boolean inverted = false;
    private boolean unique = false;
    
    private boolean startOnly = false;

    private int filterFlags = 0;
    private int requiredFlags = 0;
    
    private List<String> requiredTags = null;
    
    private Orientation orient = Orientation.UNSTRANDED;

    @Option(desc = "Only keep properly paired reads", name = "proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            requiredFlags |= ReadUtils.PROPER_PAIR_FLAG;
            filterFlags |= ReadUtils.MATE_UNMAPPED_FLAG;
        }
    }
    
    @Option(desc="List of required GTF tag annotations (comma-separated list)", name="gtf-tag", allowMultiple=true)
    public void setRequiredTags(String requiredTags) {
    	if (this.requiredTags == null) {
    		this.requiredTags = new ArrayList<String>();
    	}
    	for (String s:requiredTags.split(",")) {
    		this.requiredTags.add(s);
    	}
    }


    @Option(desc = "Filtering flags", name = "filter-flags", defaultValue = "3844")
    public void setFilterFlags(int flag) {
        filterFlags = flag;
    }

    @Option(desc = "Required flags", name = "required-flags", defaultValue = "0")
    public void setRequiredFlags(int flag) {
        requiredFlags = flag;
    }
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(desc="Count bins of size [value]", name="bins", defaultValue="0")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(desc="Count reads for genes (GTF model)", name="gtf", helpValue="fname")
    public void setGTFFile(String gtfFilename) {
        this.gtfFilename = gtfFilename;
    }

    @Option(desc="Count reads within Tabix-indexed defined regions", name="tabix", helpValue="fname")
    public void setTabixFile(String fname) {
        this.tabixFilename = fname;
    }

    @Option(desc="Count reads within BED regions", name="bed", helpValue="fname")
    public void setBedFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }
    
    @Option(desc="Extend BED regions by this many bases (news start/end not shown in output)", name="bed-extend", defaultValue="0")
    public void setBedExtend(int bedExtend) throws CommandArgumentException {
        if (bedExtend < 0) {
        	throw new CommandArgumentException("--bed-extend must be positive!");
        }
        this.bedExtend = bedExtend;
    }


    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(desc="Only count uniquely mapped reads (requires NH or IH tags)", name="unique")
    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    @Option(desc="Read must be completely contained within region", name="contained")
    public void setContained(boolean contained) {
        this.contained = contained;
    }

    @Option(desc="Library is in FR orientation", name="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(desc="Library is in RF orientation", name="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(desc="Library is in unstranded orientation (default)", name="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Option(desc="Also report the number/ratio of properly-paired reads", name="report-proper")
    public void setReportProperPairs(boolean val) {
        proper = val;
    }

    @Option(desc="Also report the average insert-size of reads", name="insert-size")
    public void setInsertSize(boolean val) {
        insert = val;
    }

    @Option(desc="Also report the number of inverted reads (FF,RR)", name="inverted")
    public void setInverted(boolean val) {
        inverted = val;
    }

    @Option(desc="Only count the starting mapped position (strand specific, for pairs - only counts the first pair)", name="startonly")
    public void setStartOnly(boolean val) {
        startOnly = val;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        int sources = 0;
        
        if (binSize > 0) {
            sources++;
        }
        if (bedFilename != null) {
            sources++;
        } 
        if (gtfFilename != null) {
            sources++;
        }
        if (tabixFilename != null) {
            sources++;
        }
        if (sources != 1) {
            throw new CommandArgumentException("You must specify one of --bins, --bed, --tabix, --vcf or --gtf!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        writer.write_line("## library-orientation: " + orient.toString());
        writer.write_line("## counts: number of reads ");
        if (proper) {
            writer.write_line("## counts: number of properly-paired reads (and not-proper pairs, and not-proper:proper ratio) ");
        }
        if (insert) {
            writer.write_line("## counts: average insert-size ");
        }
        if (inverted) {
            writer.write_line("## counts: number of inverted (FF, RR) reads ");
        }

        if (startOnly) {
            writer.write_line("## counts: starting positions only ");
        }

        if (requiredTags != null) {
            writer.write_line("## gtf-tags: " + StringUtils.join(",", requiredTags));
        }

        SamReader reader = readerFactory.open(new File(samFilename));
        String name;
        SpanSource spanSource = null;
        if (binSize > 0) {
            writer.write_line("## source: bins " + binSize);
            spanSource = new BinSpans(reader.getFileHeader().getSequenceDictionary(), binSize, orient);
            name = "bins - "+ binSize;
        } else if (bedFilename != null) {
            writer.write_line("## source: bed " + bedFilename);
            if (bedExtend > 0) {
            	writer.write_line("## bed-extend: " + bedExtend);
            }
            spanSource = new BedSpans(bedFilename, bedExtend);
            name = bedFilename;
        } else if (tabixFilename != null) {
            writer.write_line("## source: tabix " + tabixFilename);
            spanSource = new TabixSpans(tabixFilename);
            name = tabixFilename;
        } else if (gtfFilename != null) {
            writer.write_line("## source: gtf " + gtfFilename);
            spanSource = new GTFSpans(gtfFilename, requiredTags);
            name = gtfFilename;
        } else {
            reader.close();
            writer.close();
            throw new CommandArgumentException("You must specify either a bin-size, a BED file, or a GTF file!");
        }
        
        // write header cols
        for (String header: spanSource.getHeader()) {
            writer.write(header);
        }
        writer.write("read_count");
        if (proper) {
            writer.write("proper");
            writer.write("not_proper");
            writer.write("proper_ratio");
        }
        if (insert) {
            writer.write("ave_insert_size");
        }
        if (inverted) {
            writer.write("inverted_count");
        }
        writer.eol();

        
        int spanCount = 0;
        boolean missingReferences = false;
        
        for (SpanGroup spanGroup: IterUtils.wrap(ProgressUtils.getIterator(name, spanSource.iterator(), new IncrementingStats(spanSource.size())))) {
            if (spanGroup == null) {
                continue;
            }
            spanCount ++;
            if (verbose && spanCount % 1000 == 0) {
                System.err.println("[" +spanCount + "]" + spanGroup.getRefName()+":"+spanGroup.getStart());
                System.err.flush();
            }
            if (reader.getFileHeader().getSequence(spanGroup.getRefName()) == null) {
                missingReferences = true;
                continue;
            }
            
            int count = 0;
            int proper_count = 0;
            int notproper_count = 0;
            int insert_count = 0;
            long insert_acc = 0;
            int inverted_count = 0;
            
            Set<String> reads = new HashSet<String>();

            for (GenomeSpan span: spanGroup) {
                
                int spanStart = span.start+1;
                int spanEnd = span.end;
                SAMRecordIterator it = reader.query(spanGroup.getRefName(), spanStart, spanEnd, contained);
                while (it.hasNext()) {
                    SAMRecord read = it.next();
                    
                    if ((read.getFlags() & requiredFlags) != requiredFlags) {
                        // if missing a required flag, skip
                        continue;
                    }

                    if ((read.getFlags() & filterFlags) > 0) {
                        // if has any filter flag, skip
                        continue;
                    }

                    if (!reads.contains(read.getReadName())) {
                        if (spanGroup.getStrand() == Strand.NONE || orient == Orientation.UNSTRANDED || (ReadUtils.getFragmentEffectiveStrand(read, orient) == spanGroup.getStrand())) {
                            if (startOnly) {                                
                                if (read.getReadPairedFlag() && read.getSecondOfPairFlag()) {
                                    continue;
                                }
                                
                                int startpos;
                                if (ReadUtils.getFragmentEffectiveStrand(read, orient) == Strand.PLUS) {
                                    startpos = read.getAlignmentStart()-1;
                                } else {
                                    startpos = read.getAlignmentEnd();
                                }
                                
                                if (!span.contains(new GenomeSpan(spanGroup.getRefName(), startpos))) {
                                    continue;
                                }
                                
                            }

                            // is any part of the read w/in the span?
                            boolean inspan = false;
                            for (int j=1; j<=read.getReadLength(); j++) {
                                int refpos = read.getReferencePositionAtReadPosition(j) - 1;
                                if (spanStart <=  refpos && refpos < spanEnd) {
                                    inspan=true;
                                    break;
                                }
                            }
                            
                            if (!inspan) {
                                continue;
                            }
                            
                            reads.add(read.getReadName());
                            
                            if (unique && !ReadUtils.isReadUniquelyMapped(read)) {
                                continue;                                    
                            }
                            
                            count ++;
                            if (proper) {
                                if (read.getReadPairedFlag() && read.getProperPairFlag()) {
                                    proper_count ++;
                                } else if (read.getReadPairedFlag() && !read.getProperPairFlag()) {
                                    notproper_count ++;
                                }
                            }
                            if (insert) {
                                if (read.getReadPairedFlag() && read.getProperPairFlag()) {
                                    insert_acc += Math.abs(read.getInferredInsertSize());
                                    insert_count ++;
                                }
                            }
                            if (inverted) {
                                if (read.getReadPairedFlag() && read.getProperPairFlag() && read.getReadNegativeStrandFlag() == read.getMateNegativeStrandFlag()) {
                                    inverted_count ++;
                                }
                            }
                        }
                    }
                }
                it.close();
            }

            writer.write(spanGroup.getFields());
            writer.write(count);
            if (proper) {
                writer.write(proper_count);
                writer.write(notproper_count);
                if (proper_count > 0) {
                    writer.write((double) notproper_count / proper_count);
                } else {
                    writer.write(0);
                }
            }
            if (insert) {
                if (insert_count > 0) {
                    writer.write((double) insert_acc / insert_count);
                } else {
                    writer.write(0);
                }
            }
            if (inverted) {
                writer.write(inverted_count);
            }
            writer.eol();
        }

        writer.close();
        reader.close();
        if (missingReferences) {
            System.err.println("WARNING: Some references/chromosomes in the GTF file were not found in the BAM file");
        }
    }
    
    protected int calcTranscriptSize(int[] starts, int[] ends) {
        List<Integer[]> intervals = new ArrayList<Integer[]>();

        for (int i=0; i<starts.length; i++) {
            boolean found = false;
            
            for (Integer[] interval: intervals) {
                int qstart = interval[0];
                int qend = interval[1];
                
                if (
                    (qstart < starts[i] && starts[i] < qend) || 
                    (qstart < ends[i] && ends[i] < qend) ||
                    (starts[i] < qstart && qstart < ends[i]) || 
                    (starts[i] < qend && qend < ends[i]) 
                    ) {
                    found = true;
                    interval[0] = Math.min(qstart, starts[i]);
                    interval[1] = Math.max(qend, ends[i]);
                    break;
                }
                
            }
            
            if (!found) {
                intervals.add(new Integer[] { starts[i], ends[i]});
            }
        }
        
        int acc = 0;
        for (Integer[] interval: intervals) {
            acc += (interval[1] - interval[0]);
        }
        
        return acc;
    }
}
