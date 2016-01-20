package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
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
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Command(name="bam-discord", desc="Extract all discordant reads from a BAM file", category="bam")
public class BamDiscord extends AbstractCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;

    private int intraChromDistance = 5000;
    
    private String concordFilename = null;
    private String discordFilename = null;
    private String tmpDir = null;
    
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
    
    @Option(desc="Write temporary files here", name="tmpdir", helpValue="dir")
    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Option(desc = "Maximum intra-chromasomal distance", name="dist", defaultValue="50")
    public void setIntraChromDistance(int intraChromDistance) {
        this.intraChromDistance = intraChromDistance;
    }    

    @Option(desc = "Discordant read output BAM", name="discord")
    public void setDiscordFilename(String discordFilename) {
        this.discordFilename = discordFilename;
    }    

    @Option(desc = "Concordant read output BAM", name="concord")
    public void setConcordFilename(String concordFilename) {
        this.concordFilename = concordFilename;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        
        if (concordFilename == null && discordFilename == null) {
            throw new CommandArgumentException("You must specify --discord or --concord (or both)!");
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

        SAMFileWriter concord = null;
        SAMFileWriter discord = null;

        if (concordFilename != null) {
            SAMFileWriterFactory factory = new SAMFileWriterFactory();
    
            File outfile = null;
            OutputStream outStream = null;
            
            if (concordFilename.equals("-")) {
                outStream = new BufferedOutputStream(System.out);
            } else {
                outfile = new File(concordFilename);
            }
            
            if (tmpDir != null) {
                factory.setTempDirectory(new File(tmpDir));
            } else if (outfile == null || outfile.getParent() == null) {
                factory.setTempDirectory(new File(".").getCanonicalFile());
            } else if (outfile!=null) {
                factory.setTempDirectory(outfile.getParentFile());
            }
    
            SAMFileHeader header = reader.getFileHeader().clone();
            SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("bam-discord", header);
            List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
            pgRecords.add(0, pg);
            header.setProgramRecords(pgRecords);
    
            if (outfile != null) {
                concord = factory.makeBAMWriter(header, true, outfile);
            } else {
                concord = factory.makeSAMWriter(header,  true,  outStream);
            }
        }
        
        if (discordFilename != null) {
            SAMFileWriterFactory factory = new SAMFileWriterFactory();
    
            File outfile = null;
            OutputStream outStream = null;
            
            if (discordFilename.equals("-")) {
                outStream = new BufferedOutputStream(System.out);
            } else {
                outfile = new File(discordFilename);
            }
            
            if (tmpDir != null) {
                factory.setTempDirectory(new File(tmpDir));
            } else if (outfile == null || outfile.getParent() == null) {
                factory.setTempDirectory(new File(".").getCanonicalFile());
            } else if (outfile!=null) {
                factory.setTempDirectory(outfile.getParentFile());
            }
    
            SAMFileHeader header = reader.getFileHeader().clone();
            SAMProgramRecord pg = NGSUtils.buildSAMProgramRecord("bam-discord", header);
            List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
            pgRecords.add(0, pg);
            header.setProgramRecords(pgRecords);
    
            if (outfile != null) {
                discord = factory.makeBAMWriter(header, true, outfile);
            } else {
                discord = factory.makeSAMWriter(header,  true,  outStream);
            }
        }

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        long totalCount = 0;
        long discordCount = 0;
        
        while (it.hasNext()) {
            totalCount++;
            SAMRecord read = it.next();
            
            if (ReadUtils.isDiscordant(read, intraChromDistance)) {
                discordCount++;
                if (discord != null) {
                    discord.addAlignment(read);
                }
            } else if (concord != null) {
                concord.addAlignment(read);
            }
        }
        reader.close();
        if (discord != null) {
            discord.close();
        }
        if (concord != null) {
            concord.close();
        }
        System.err.println("Total reads     : "+totalCount);
        System.err.println("Discordant reads: "+discordCount);
    }
}
