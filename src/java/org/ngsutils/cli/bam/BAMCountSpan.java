package org.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.sf.samtools.SAMFileReader;
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

@CommandLineInterface(application="ngsutilsj bam-count-span")
@Command(name="bam-count-span", desc="Counts the number of reads within a span (BED or bins)", cat="bam")
public class BAMCountSpan extends AbstractOutputCommand {
    private SAMFileReader reader;
    private String bedFilename=null;
    private int binSize = 0;
    private boolean contained = false;
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) throws IOException {
        this.reader = new SAMFileReader(new File(filename));
    }

    @Option(description = "Bin size", longName="bins", defaultValue="0")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(description = "BED file", longName="bed", defaultToNull=true)
    public void setBedFile(String bedFilename) {
        this.bedFilename = bedFilename;
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

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (binSize > 0 && bedFilename != null) {
            throw new NGSUtilsException("You can't specify both a bin-size and a BED file!");
        }

        TabWriter writer = new TabWriter();
        writer.write_line("## " + NGSUtils.getVersion());
        writer.write_line("## library-orientation " + orient.toString());
        
        Iterable<Span> spanIter = null;
        if (binSize > 0) {
            writer.write_line("## bins " + binSize);
            spanIter = new BinSpans(reader.getFileHeader().getSequenceDictionary(), binSize, orient);
        } else if (bedFilename != null) {
            writer.write_line("## bed " + bedFilename);
            spanIter = new BEDSpans(bedFilename);
        } else {
            throw new NGSUtilsException("You must specify either a bin-size or a BED file!");
        }

        for (Span span: spanIter) {
            int count = 0;
            Set<String> reads = new HashSet<String>();
            
            for (int i = 0; i < span.getStarts().length; i++) {            
                // reader.query is 1-based
                SAMRecordIterator it = reader.query(span.getRefName(), span.getStarts()[i]+1, span.getEnds()[i], contained);
                while (it.hasNext()) {
                    SAMRecord read = it.next();
                    if (!reads.contains(read.getReadName())) {
                        reads.add(read.getReadName());
                        if (span.getStrand() == Strand.NONE || (ReadUtils.getFragmentEffectiveStrand(read, orient) == span.getStrand())) {
                            count ++;
                        }
                    }
                }
                it.close();
            }

            writer.write(span.getFields());
            writer.write(count);
            writer.eol();
        }

        writer.close();
    }
}
