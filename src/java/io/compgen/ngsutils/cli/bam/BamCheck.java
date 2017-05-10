package io.compgen.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

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
import io.compgen.common.RadixSet;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-check", desc="Checks a BAM file to make sure it is valid", category="bam")
public class BamCheck extends AbstractCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean mates = false;

    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Check for missing read-mates (paired-end)", name="mates")
    public void setMates(boolean mates) {
        this.mates = mates;
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

        final RadixSet setOne = new RadixSet();
        final RadixSet setTwo = new RadixSet();
        final RadixSet goodReads = new RadixSet();

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName() + " "+setOne.size()+"/"+setTwo.size()+"/"+goodReads.size();
            }}, new CloseableFinalizer<SAMRecord>());
        long i = 0;
        SAMRecord lastRead = null;
        int error = 0;
        while (it.hasNext()) {
            i++;
            SAMRecord read = it.next();
            
            if (read.getReadName() == null) {
                System.err.println("\nERROR: Read-name empty - bad file? (last good read: "+lastRead.getReadName()+" "+lastRead.getReferenceName()+":"+lastRead.getAlignmentStart()+")");
                error++;
            }
            
            lastRead = read;
            
            if (read.getBaseQualities().length != read.getReadBases().length) {
                System.err.println("\nERROR: Base calls / quality length mismatch: "+read.getReadName()+")");
                error++;
            }
            
            if (read.getReadPairedFlag() && mates) {
                String readname = read.getReadName();
                if (!goodReads.contains(readname)) {
                    if (read.getFirstOfPairFlag()) {
                        if (setTwo.contains(readname)) {
                            setTwo.remove(readname);
                            if (!ReadUtils.isReadUniquelyMapped(read)) {
                                goodReads.add(readname);
                            }
                        } else {
                            setOne.add(readname);
                        }
                    } else {
                        if (setOne.contains(readname)) {
                            setOne.remove(readname);
                            if (!ReadUtils.isReadUniquelyMapped(read)) {
                                goodReads.add(readname);
                            }
                        } else {
                            setTwo.add(readname);
                        }
                    }
                }
            }
        }
        reader.close();
        if (mates) {
            System.err.println("Reads with missing mates: "+ (setOne.size()+setTwo.size()));
            if (setOne.size() > 0) {
                Iterator<String> it2 = setOne.iterator();
                for (int j=0; j<10 && setOne.size() > j; j++) {
                    System.err.println("Example read: "+it2.next());
                    error++;
                }
            }
        }
        
        if (error>0) {
            System.err.println("File had errors!");
            System.err.println("Total reads: "+i);
            System.err.println("Errors: "+error);
            System.exit(1);
        }
        System.err.println("Successfully read "+i+" records.");
    }
}
