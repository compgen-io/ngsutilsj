package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
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
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Command(name="bam-split", desc="Split a BAM file into smaller files", category="bam")
public class BamSplit extends AbstractCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean byRef = false;
    private boolean unmapped = false;
    private int readCount = -1;
    private String outTemplate = null;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Split by reference (chromosome)", name="by-ref")
    public void setByRef(boolean byRef) {
        this.byRef = byRef;
    }

    @Option(desc = "Include unmapped reads in the output", name="unmapped")
    public void setUnmapped(boolean unmapped) {
        this.unmapped = unmapped;
    }

    @Option(desc = "Split into chunks with N reads", name="read-count", helpValue="N")
    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }
    
    @Option(desc="Output filename template (files will be named templ.NNN.bam or templ.chr??.bam)", name="out", helpValue="templ")
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        if (outTemplate == null) {
            throw new CommandArgumentException("You must specify an output filename template (--out)!");
        }
        
        if (readCount == -1 && !byRef) {
            throw new CommandArgumentException("You must specify either --read-count or --by-ref!");
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

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());

        
        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileHeader header = reader.getFileHeader().clone();
        SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("bam-split", header);
        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
        pgRecords.add(0, pg);
        header.setProgramRecords(pgRecords);

        SAMFileWriter out = null;
        String lastRef = null;
        int curPos = -1;
        
        int i = 0;
        int chunk = 0;

        Map<String, SAMFileWriter> refWriters = new HashMap<String, SAMFileWriter>();
        if (byRef) {
            for (SAMSequenceRecord ref: header.getSequenceDictionary().getSequences()) {
                refWriters.put(ref.getSequenceName(), factory.makeBAMWriter(header, true, new File(outTemplate+"."+ref.getSequenceName()+".bam")));
            }
            if (unmapped) {
                refWriters.put("UNMAPPED", factory.makeBAMWriter(header, true, new File(outTemplate+"."+"UNMAPPED"+".bam")));
            }
        }

        
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.getReadUnmappedFlag() && !unmapped) {
                continue;
            }
            
            if (byRef) {
                String ref = read.getReferenceName();
                if (ref == null || read.getReadUnmappedFlag()) {
                    ref = "UNMAPPED";                    
                }
                refWriters.get(ref).addAlignment(read);
                if (header.getSortOrder() == SortOrder.coordinate) {
                    if (lastRef != null && !ref.equals(lastRef)) {
                        refWriters.get(lastRef).close();
                        refWriters.remove(lastRef);
                    }
                    lastRef = read.getReferenceName();
                }
            } else {
                if (out == null) {
                    chunk++;
                    i = 0;
                    out = factory.makeBAMWriter(header, true, new File(outTemplate+"."+chunk+".bam"));
                } else if (header.getSortOrder() == SortOrder.coordinate) {
                    // If the BAM file is sorted, keep all reads that start at the same position together...
                    if (!lastRef.equals(read.getReferenceName()) || (i >= readCount && read.getAlignmentStart() != curPos)) {
                        out.close();
                        chunk++;
                        i = 0;
                        out = factory.makeBAMWriter(header, true, new File(outTemplate+"."+chunk+".bam"));
                    }
                } else {
                    // If the BAM file isn't sorted, just make it a firm split.
                    if (i >= readCount) {
                        out.close();
                        chunk++;
                        i = 0;
                        out = factory.makeBAMWriter(header, true, new File(outTemplate+"."+chunk+".bam"));
                    }
                }
                i++;
                out.addAlignment(read);
                curPos = read.getAlignmentStart();
                lastRef = read.getReferenceName();
            }
        }
        
        for (String ref: refWriters.keySet()) {
            refWriters.get(ref).close();
        }
        
        if (out != null) {
            out.close();
        }
        reader.close();
    }
}
