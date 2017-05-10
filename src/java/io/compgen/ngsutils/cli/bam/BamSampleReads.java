package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import htsjdk.samtools.SAMRecord;
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
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-sample", desc="Create a whitelist of read names sampled randomly from a file", category="bam", experimental=true)
public class BamSampleReads extends AbstractCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;

    private String outputFilename = null;
    private long sampleSize = -1;
    private int numberOfSamplings = 1;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(desc = "Output file template (will be named out.1.txt, out.2.txt...)", name="output", required=true)
    public void setOutputFilename(String outputFilename) {
        this.outputFilename = outputFilename;
    }

    @Option(desc = "Number of reads to return", charName="n", name="reads", required=true)
    public void setSampleSize(long sampleSize) {
        this.sampleSize = sampleSize;
    }    

    @Option(desc = "Number of sample lists to generate", name="lists", defaultValue="1")
    public void setNumberOfSamplings(int numberOfSamplings) {
        this.numberOfSamplings = numberOfSamplings;
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

        Set<String> readNames = new HashSet<String>();

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());

        System.err.print("Reading file...");
        
        while (it.hasNext()) {
            readNames.add(it.next().getReadName());
        }

        System.err.println(" done");
        
        reader.close();
        List<String> readNameList = new ArrayList<String>(readNames);
        readNames.clear();
        
        for (int i=1; i<=numberOfSamplings; i++) {
            System.err.println("Generating list #"+i+"...");
            Random rdm = new Random();
            
            Set<String> keptReads = new HashSet<String>();
            int j=0;
            while (j<sampleSize) {
                int idx = rdm.nextInt(readNameList.size());
                if (!keptReads.contains(readNameList.get(idx))) {
                    keptReads.add(readNameList.get(idx));
                    j++;
                }
            }
            
            OutputStream os = new FileOutputStream(outputFilename+"."+i+".txt");
            for (String readName: keptReads) {
                os.write((readName+"\n").getBytes());
            }
            os.close();            
        }
    }
}
