package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;


// TODO: Add % bases >= Q30
// TODO: Add effective coverage (based on # of bases, and reference size)

@Command(name="bam-refcount", desc="Only count the number of reads aligned to each reference (only R1 is counted)", category="bam")
public class BamRefCount extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean unique = false;
    private boolean showUnmappedRef = false;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Display reference counts even if they have no reads mapped to them", name="show-unmapped-ref")
    public void setShowUnmappedRef(boolean showUnmappedRef) {
        this.showUnmappedRef = showUnmappedRef;
    }

    @Option(desc="Only count uniquely aligned reads", name="unique")
    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    


    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }

        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        InputStream is = null;
        SamReader reader = null;
        String name;
        FileChannel channel = null;
        
        if (filename.equals("-")) {
            is = System.in;
            name = "<stdin>";
        } else {
            File f = new File(filename);
            is = new FileInputStream(f);
            channel = ((FileInputStream)is).getChannel();
            name = f.getName();
        }

        reader = readerFactory.open(SamInputResource.of(is));
        
        Map<String, Integer> refCounts = new HashMap<String, Integer>();
        for (SAMSequenceRecord ref: reader.getFileHeader().getSequenceDictionary().getSequences()) {
            refCounts.put(ref.getSequenceName(), 0);
        }
        
        Iterator<SAMRecord> it;
        if (channel == null) {
            it = reader.iterator(); 
        } else {
            it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), 
                new ProgressMessage<SAMRecord>() {
                    long i = 0;
                    @Override
                    public String msg(SAMRecord current) {
                        i++;
                        return i+" "+current.getReadName();
                    }
                }, new CloseableFinalizer<SAMRecord>(){});
        }

        for (SAMRecord read: IterUtils.wrap(it)) {
            if (read.getReadPairedFlag() && read.getSecondOfPairFlag()) {
                // We only profile the first read of a pair...
                continue;
            }
            
            if (!read.getDuplicateReadFlag() && !read.getNotPrimaryAlignmentFlag() && !read.getReadUnmappedFlag() && (ReadUtils.isReadUniquelyMapped(read) || !unique)) {
                refCounts.put(read.getReferenceName(), refCounts.get(read.getReferenceName())+1);
            }            
        }
        
        reader.close();

        for (String ref: StringUtils.naturalSort(refCounts.keySet())) {
            if (showUnmappedRef || refCounts.get(ref) > 0) {
                println(ref+"\t"+refCounts.get(ref));
            }
        }

    }
    
    private void println(String s) throws IOException {
        out.write((s+"\n").getBytes());
    }
}
