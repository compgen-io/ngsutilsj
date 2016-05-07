package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMReadGroupRecord;
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
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Command(name="bam-concat", desc="Concatenates BAM files (handles @RG, @PG)", category="bam")
public class BamConcat extends AbstractCommand {
    
    private String[] inputNames = null;
    private String outputName = null;
    
    private boolean useSameRGID = false; 
    
    private boolean lenient = false;
    private boolean silent = false;
    
    @UnnamedArg(name = "output input1 input2...")
    public void setFilename(String[] filenames) {
        if (filenames.length > 0) {
            outputName = filenames[0];
        }
        
        if (filenames.length > 1) {
            this.inputNames = new String[filenames.length-1];
            for (int i=1; i<filenames.length; i++) {
                this.inputNames[i-1] = filenames[i];
            }
        }
    }

    @Option(desc = "Input files use the same @RG IDs, so don't rename overlaping names", name="same-rgid")
    public void setUseSameRGID(boolean useSameRGID) {
        this.useSameRGID = useSameRGID;
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
        if (outputName == null) {
            throw new CommandArgumentException("You must specify an output file!");
        }
        if (inputNames == null || inputNames.length<2) {
            throw new CommandArgumentException("You must specify at least 2 input files!");
        }
        int stdinCount = 0;
        for (String filename: inputNames) {
            if (filename.equals("-")) {
                stdinCount++;
            }
        }

        if (stdinCount > 1) {
            throw new CommandArgumentException("You may not use stdin for more than one input!");
        }
        
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }
        
        SamReader[] readers = new SamReader[inputNames.length];
        
        // setup outputs
        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter writer = null;

        SAMFileHeader header = readers[0].getFileHeader().clone();
        header.setSortOrder(SortOrder.unsorted);

        List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>();
        List<SAMReadGroupRecord> rgRecords = new ArrayList<SAMReadGroupRecord>();
        
        List<Map<String, String>> rgIdRemap = new ArrayList<Map<String,String>>();
        
        for (int i=0; i<inputNames.length; i++) {
            rgIdRemap.add(null);
            
            for (SAMProgramRecord pgRec: readers[i].getFileHeader().getProgramRecords()) {
                String suffix = "_"+(i+1);
                
                int j=0;
                boolean found = true;
                while (found) {
                    found = false;
                    for (SAMProgramRecord tmp: pgRecords) {
                        if (tmp.getId().equals(pgRec.getId()+suffix)) {
                            found = true;
                        }
                    }
                    
                    if (found) {
                        j++;
                        suffix = "_"+(i+1)+"_"+j;
                    }
                }
                
                pgRecords.add(BamHeaderUtils.suffixAddSAMProgramRecord(pgRec, suffix));
            }
            for (SAMReadGroupRecord rgr: readers[i].getFileHeader().getReadGroups()) {
                boolean found = true;
                String rgid = rgr.getId();
                int j = 0;
                while (found) {
                    found=false;
                    for (SAMReadGroupRecord tmp: rgRecords) {
                        if (tmp.getId().equals(rgid)) {
                            found = true;
                        }
                    }
                    if (found) {
                        if (useSameRGID) {
                            break;
                        }
                        if (j > 0) {
                            rgid =  rgr.getId() + "_" + (i+1) + "_" + j;
                        } else {
                            rgid =  rgr.getId() + "_" + (i+1);
                        }
                        j++;
                    } else {
                        if (!rgid.equals(rgr.getId())) {
                            if (rgIdRemap.get(i) == null) {
                                rgIdRemap.set(i, new HashMap<String,String>());
                            }
                            rgIdRemap.get(i).put(rgr.getId(), rgid);
                        }
                        rgRecords.add(BamHeaderUtils.SAMReadGroupRecordNewID(rgr, rgid));
                    }
                }
            }
        }

        pgRecords.add(0, BamHeaderUtils.buildSAMProgramRecord("bam-concat", pgRecords));
        header.setProgramRecords(pgRecords);
        header.setReadGroups(rgRecords);
    
        if (outputName.equals("-")) {
            writer = factory.makeBAMWriter(header, true, new BufferedOutputStream(System.out));
        } else {
            writer = factory.makeBAMWriter(header, true, new File(outputName));
        }
        
        long total = 0;
        final int[] inputCounts = new int[1];
        for (int i=0; i<inputNames.length; i++) {
            Iterator<SAMRecord> it = null;
            inputCounts[0] = 0;
            if (inputNames[i].equals("-")) {
                readers[i] = readerFactory.open(SamInputResource.of(System.in));
                it = readers[i].iterator();
            } else { 
                File f = new File(inputNames[i]);
                FileInputStream fis = new FileInputStream(f);
                readers[i] = readerFactory.open(SamInputResource.of(fis));
                FileChannel channel = fis.getChannel();

                final int idx = i;
                
                it = ProgressUtils.getIterator(inputNames[i], readers[i].iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
                  @Override
                  public String msg(SAMRecord current) {
                      return inputNames[idx]+":"+inputCounts[0];
                  }}, new CloseableFinalizer<SAMRecord>());
            }

            while (it.hasNext()) {
                SAMRecord read = it.next();
                
                if (rgIdRemap.get(i) != null) {
                    String orig = (String) read.getAttribute("RG");
                    if (orig!=null && rgIdRemap.get(i).containsKey(orig)) {
                        read.setAttribute("RG", rgIdRemap.get(i).get(orig));
                    }
                }
                
                writer.addAlignment(read);
                inputCounts[0]++;
            }
            System.err.println(inputNames[i]+": "+inputCounts[0]);
            total += inputCounts[0];
            readers[i].close();
        }
        writer.close();
        
        System.err.println("Total reads: "+total);
    }
}
