package org.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-check")
@Command(name="bam-check", desc="Checks a BAM file to make sure it is valid", cat="bam", experimental=true)
public class BamCheck extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;

    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filename == null) {
            throw new ArgumentValidationException("You must specify an input BAM filename!");
        }
        
        SAMFileReader reader;
        FileChannel channel = null;
        String name;
        if (filename.equals("-")) {
            reader = new SAMFileReader(System.in);
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = new SAMFileReader(fis);
            name = f.getName();
        }
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }});;
        long i = 0;
        while (it.hasNext()) {
            i++;
            it.next();
        }
        reader.close();
        System.err.println("Successfully read: "+i+" records.");
    }
}
