package org.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.cli.bam.count.BEDSpans;
import org.ngsutils.cli.bam.count.BinSpans;
import org.ngsutils.cli.bam.count.Span;
import org.ngsutils.cli.bam.count.SpanSource;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-count")
@Command(name="bam-count", desc="Counts the number of reads within a BED region or by bins (--bed or --bins required)", cat="bam")
public class BAMCount extends AbstractOutputCommand {
    
    private String samFilename=null;
    
    private String bedFilename=null;
    private int binSize = 0;
    
    private boolean contained = false;
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean proper = false;
    private boolean insert = false;
    private boolean inverted = false;
    
    private boolean startOnly = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(description = "Count bins of size [value]", longName="bins", defaultValue="0")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(description = "Count reads within BED regions", longName="bed", defaultToNull=true)
    public void setBedFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(description = "Read must be completely contained within region", longName="contained")
    public void setContained(boolean contained) {
        this.contained = contained;
    }

    @Option(description = "Library is in FR orientation", longName="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(description = "Library is in RF orientation", longName="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(description = "Library is in unstranded orientation (default)", longName="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Option(description = "Also report the number/ratio of properly-paired reads", longName="proper-pairs")
    public void setProperPairs(boolean val) {
        proper = val;
    }

    @Option(description = "Also report the average insert-size of reads", longName="insert-size")
    public void setInsertSize(boolean val) {
        insert = val;
    }

    @Option(description = "Also report the number of inverted reads (FF,RR)", longName="inverted")
    public void setInverted(boolean val) {
        inverted = val;
    }

    @Option(description = "Only count the starting mapped position (strand specific)", longName="startonly")
    public void setStartOnly(boolean val) {
        startOnly = val;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (binSize > 0 && bedFilename != null) {
            throw new NGSUtilsException("You can't specify both a bin-size and a BED file!");
        }
        
        SAMFileReader reader = new SAMFileReader(new File(samFilename));
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        TabWriter writer = new TabWriter();
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        writer.write_line("## library-orientation: " + orient.toString());
        
        SpanSource spanSource = null;
        if (binSize > 0) {
            writer.write_line("## source: bins " + binSize);
            spanSource = new BinSpans(reader.getFileHeader().getSequenceDictionary(), binSize, orient);
        } else if (bedFilename != null) {
            writer.write_line("## source: bed " + bedFilename);
            spanSource = new BEDSpans(bedFilename);
        } else {
            reader.close();
            writer.close();
            throw new NGSUtilsException("You must specify either a bin-size or a BED file!");
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
        
        for (Span span: spanSource) {
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

                    if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag()) {
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
