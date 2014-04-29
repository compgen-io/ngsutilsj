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
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.cli.bam.count.BEDSpans;
import org.ngsutils.cli.bam.count.BinSpans;
import org.ngsutils.cli.bam.count.Span;
import org.ngsutils.support.ReadUtils;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-count")
@Command(name="bam-count", desc="Counts the number of reads within a BED region or by bins (--bed or --bins required)", cat="bam")
public class BAMCount extends AbstractOutputCommand {
    
    private enum CountType {
        READ_COUNT,
        PROPER_PAIRS,
        INSERT_SIZE;
    };
    
    private String samFilename=null;
    
    private String bedFilename=null;
    private int binSize = 0;
    
    private boolean contained = false;
    private boolean lenient = false;
    private boolean silent = false;
    
    private CountType countType = CountType.READ_COUNT;
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

    @Option(description = "Tally number of properly-paired reads", longName="proper-pairs")
    public void setProperPairs(boolean val) {
        if (val) {
            countType = CountType.PROPER_PAIRS;
        }
    }

    @Option(description = "Tally average insert-size of reads", longName="insert-size")
    public void setInsertSize(boolean val) {
        if (val) {
            countType = CountType.INSERT_SIZE;
        }
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (binSize > 0 && bedFilename != null) {
            throw new NGSUtilsException("You can't specify both a bin-size and a BED file!");
        }
        
        SAMFileReader reader = new SAMFileReader(new File(samFilename));
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        }
        if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        TabWriter writer = new TabWriter();
        writer.write_line("## " + NGSUtils.getVersion());
        writer.write_line("## library-orientation: " + orient.toString());
        
        Iterable<Span> spanIter = null;
        if (binSize > 0) {
            writer.write_line("## source: bins " + binSize);
            spanIter = new BinSpans(reader.getFileHeader().getSequenceDictionary(), binSize, orient);
        } else if (bedFilename != null) {
            writer.write_line("## source: bed " + bedFilename);
            spanIter = new BEDSpans(bedFilename);
        } else {
            reader.close();
            writer.close();
            throw new NGSUtilsException("You must specify either a bin-size or a BED file!");
        }

        if (countType == CountType.READ_COUNT) {
            writer.write_line("## counts: number of reads ");
        } else if (countType == CountType.PROPER_PAIRS) {
            writer.write_line("## counts: number of properly-paired reads ");
        } else if (countType == CountType.INSERT_SIZE) {
            writer.write_line("## counts: average insert-size ");
        }

        int spanCount = 0;
        
        for (Span span: spanIter) {
            spanCount ++;
            if (verbose && spanCount % 1000 == 0) {
                System.err.println("[" +spanCount + "]" + span.getRefName()+":"+span.getStarts()[0]);
                System.err.flush();
            }
            
            int count = 0;
            long acc = 0;
            Set<String> reads = new HashSet<String>();
            
            for (int i = 0; i < span.getStarts().length; i++) {            
                // reader.query is 1-based
                SAMRecordIterator it = reader.query(span.getRefName(), span.getStarts()[i]+1, span.getEnds()[i], contained);
                while (it.hasNext()) {
                    SAMRecord read = it.next();
                    if (!reads.contains(read.getReadName())) {
                        reads.add(read.getReadName());
                        if (span.getStrand() == Strand.NONE || (ReadUtils.getFragmentEffectiveStrand(read, orient) == span.getStrand())) {
                            if (countType == CountType.READ_COUNT) {
                                count ++;
                            } else if (countType == CountType.PROPER_PAIRS) {
                                if (read.getReadPairedFlag() && read.getProperPairFlag()) {
                                    count ++;
                                }
                            } else if (countType == CountType.INSERT_SIZE) {
                                if (read.getReadPairedFlag() && read.getProperPairFlag()) {
                                    count ++;
                                    acc += read.getInferredInsertSize();
                                }
                            }
                        }
                    }
                }
                it.close();
            }

            writer.write(span.getFields());
            if (countType == CountType.READ_COUNT) {
                writer.write(count);
            } else if (countType == CountType.PROPER_PAIRS) {
                writer.write(count);
            } else if (countType == CountType.INSERT_SIZE) {
                if (count > 0) {
                    writer.write((double) acc / count);
                } else {
                    writer.write(0);
                }
            }
            writer.eol();
        }

        writer.close();
        reader.close();
    }
}
