package io.compgen.ngsutils.cli.bam;

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
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.cli.bam.count.BedSpans;
import io.compgen.ngsutils.cli.bam.count.BinSpans;
import io.compgen.ngsutils.cli.bam.count.Span;
import io.compgen.ngsutils.cli.bam.count.SpanSource;
import io.compgen.support.IterUtils;
import io.compgen.support.TabWriter;
import io.compgen.support.progress.IncrementingStats;
import io.compgen.support.progress.ProgressUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Command(name="bam-count", desc="Counts the number of reads within a BED region or by bins (--bed or --bins required)", category="bam")
public class BamCount extends AbstractOutputCommand {
    
    private String samFilename=null;
    private String bedFilename=null;
    
    private int binSize = 0;
    
    private boolean contained = false;
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean proper = false;
    private boolean insert = false;
    private boolean inverted = false;
    private boolean unique = false;
    
    private boolean startOnly = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(desc="Count bins of size [value]", name="bins", defaultValue="0")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(desc="Count reads within BED regions", name="bed")
    public void setBedFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(desc="Only count uniquely mapped reads", name="unique")
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

    @Option(desc="Also report the number/ratio of properly-paired reads", name="proper-pairs")
    public void setProperPairs(boolean val) {
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
        if (binSize > 0 && bedFilename != null) {
            throw new CommandArgumentException("You can't specify both a bin-size and a BED file!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = readerFactory.open(new File(samFilename));
        
        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        writer.write_line("## library-orientation: " + orient.toString());
        
        String name;
        SpanSource spanSource = null;
        if (binSize > 0) {
            writer.write_line("## source: bins " + binSize);
            spanSource = new BinSpans(reader.getFileHeader().getSequenceDictionary(), binSize, orient);
            name = "bins - "+ binSize;
        } else if (bedFilename != null) {
            writer.write_line("## source: bed " + bedFilename);
            spanSource = new BedSpans(bedFilename);
            name = bedFilename;
        } else { // TODO: add a GTF span source 
            reader.close();
            writer.close();
            throw new CommandArgumentException("You must specify either a bin-size or a BED file!");
        }

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
        
        for (Span span: IterUtils.wrap(ProgressUtils.getIterator(name, spanSource.iterator(), new IncrementingStats(spanSource.size())))) {
            spanCount ++;
            if (verbose && spanCount % 1000 == 0) {
                System.err.println("[" +spanCount + "]" + span.getRefName()+":"+span.getStarts()[0]);
                System.err.flush();
            }
            
            int count = 0;
            int proper_count = 0;
            int notproper_count = 0;
            int insert_count = 0;
            long insert_acc = 0;
            int inverted_count = 0;
            
            Set<String> reads = new HashSet<String>();
            
            for (int i = 0; i < span.getStarts().length; i++) {            
                // spans are 0-based, reader query is 1-based
                int spanStart = span.getStarts()[i]+1;
                int spanEnd = span.getEnds()[i];
                SAMRecordIterator it = reader.query(span.getRefName(), spanStart, spanEnd, contained);
                while (it.hasNext()) {
                    SAMRecord read = it.next();

                    if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                        // skip all secondary / duplicate / unmapped reads
                        continue;
                    }

                    if (!reads.contains(read.getReadName())) {
                        if (span.getStrand() == Strand.NONE || orient == Orientation.UNSTRANDED || (ReadUtils.getFragmentEffectiveStrand(read, orient) == span.getStrand())) {
                            if (startOnly) {                                
                                if (read.getReadPairedFlag() && read.getSecondOfPairFlag()) {
                                    continue;
                                }
                                
                                int startpos;
                                if (ReadUtils.getFragmentEffectiveStrand(read, orient) == Strand.PLUS) {
                                    startpos = read.getAlignmentStart();
                                } else {
                                    startpos = read.getAlignmentEnd();
                                }
                                
                                if (!span.within(span.getRefName(), startpos)) {
                                    continue;
                                }
                            }
                            
                            boolean inspan = false;
                            for (int j=0; j< read.getReadLength(); j++) {
                                int refpos = read.getReferencePositionAtReadPosition(j);
                                if (spanStart <=  refpos && refpos < spanEnd) {
                                    inspan=true;
                                    break;
                                }
                            }
                            
                            if (!inspan) {
                                continue;
                            }
                            
                            reads.add(read.getReadName());
                            
                            if (unique) {
                                Integer mappings = read.getIntegerAttribute("NH");
                                if (mappings == null || mappings < 0) {
                                    mappings = read.getIntegerAttribute("IH");
                                }
                                if (mappings != null && mappings > 1) {
                                    continue;
                                }
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

            writer.write(span.getFields());
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
    }
}
