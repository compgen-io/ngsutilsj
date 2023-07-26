package io.compgen.ngsutils.cli.bam;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.GroupOrder;
import htsjdk.samtools.SAMFileHeader.SortOrder;
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
import io.compgen.ngsutils.bam.support.BamHeaderUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

@Command(name="bam-best", 
         desc="With reads mapped to two bam references, determine which reads mapped best to each", 
         category="bam",
         doc="If you have mapped reads to two separate references, this command will determine, \n"
                 + "for each read, which reference they mapped best to. This can be useful for mapping \n"
                 + "RNA across splice-junctions with a splicing naive aligner, or if you have a sample \n"
                 + "that contains DNA derived from multiple organisms (e.g. xenografts, metagenomics).\n"
                 + "\n"
                 + "Files do not have to be name-sorted, but they must be in the same order. For reads \n"
                 + "with multiple mappings in the same file, only the 'best' mapping (based on name values) \n"
                 + "will be considered.\n"
                 + "\n"
                 + "You may include as many input files as you'd like. You may also include as many output \n"
                 + "files as you'd like. Each output will be matched to the corresponding input file, so \n"
                 + "that the first output will contain the reads that matched the first input best, etc... \n"
                 + "You must have at least one output file."
        )

public class BamBest extends AbstractCommand {
    private class OrderedTag {
        public final String name;
        public final boolean ascending;
        
        private OrderedTag(String val) {
            boolean ascending = true;
            if (val.endsWith("+")) {
                val = val.substring(0, val.length()-1);
            } else if (val.endsWith("-")) {
                ascending = false;
                val = val.substring(0, val.length()-1);
            }
            this.name = val;
            this.ascending = ascending;
        }
        private OrderedTag(String tag, boolean ascending) {
            this.name = tag;
            this.ascending = ascending;
        }
    }
    
    private class OrderedTagValues implements Comparable<OrderedTagValues>{
        public final OrderedTag[] tags;
        public final int[] values;
        
        private OrderedTagValues(OrderedTag[] tags, int[] values) {
            this.tags = tags;
            this.values = values;
        }

        @Override
        public int compareTo(OrderedTagValues o) {
            if (o == null) {
                return -1;
            }
            if (o == this) {
                return 0;
            }
            
            for (int i=0; i<tags.length; i++) {
                if (tags[i].ascending) {
                    if (values[i] > o.values[i]) {
                        return -1;
                    } else if (values[i] < o.values[i]) {
                        return 1;
                    }
                } else {
                    if (values[i] > o.values[i]) {
                        return 1;
                    } else if (values[i] < o.values[i]) {
                        return -1;
                    }
                }
            }
            
            return 0;
        }
        
        public String toString() {
            String s = "";
            for (int i=0; i < tags.length; i++) {
                if (i != 0) {
                    s += ",";
                }
                s += tags[i].name+":"+values[i];
            }
            return s;
        }
    }

    
    private String[] inputs = null;
    private String[] outputs = null;
    private OrderedTag[] tags = null;
    private String statsFilename = null;
    private String unmappedFilename = null;
    
    private boolean lenient = false;
    private boolean silent = false;
    private boolean allowOrphans = false;
    private boolean unsorted = false;
    private boolean clearGroup = false;
    private boolean noTies = false;

    @UnnamedArg(name = "input1 input2 {...} -- output1 {output2 ...}")
    public void setInputOutputs(String[] vals) throws CommandArgumentException {
        int inputCount = 0;
        int outputCount = 0;
        boolean flag = false;
        
        boolean stdin = false;
        boolean stdout = false;
        
        for (int i=0; i<vals.length; i++) {
            if (vals[i].equals("--")) {
                flag = true;
            } else if (flag) {
                if (vals[i].equals("-")) {
                    if (stdout) {
                        throw new CommandArgumentException("You can only have one output be to stdout!");
                    }
                    stdout = true;
                }
                outputCount++;
            } else {
                if (vals[i].equals("-")) {
                    if (stdin) {
                        throw new CommandArgumentException("You can only have one input be from stdin!");
                    }
                    stdin = true;
                }
                inputCount++;
            }
        }
        
        inputs = new String[inputCount];
        outputs = new String[outputCount];

        int idx = 0;
        for (int i=0; i<vals.length; i++) {
            if (i < inputCount) {
                inputs[idx++] = vals[i];
            } else if (i == inputCount) {
                idx = 0;
            } else {
                outputs[idx++] = vals[i];
            }
        }
    }
    @Option(desc = "Write output stats to a file", name="stats", helpValue="fname")
    public void setStatsFile(String statsFilename) {
        this.statsFilename=statsFilename;
    }
    @Option(desc = "Write unmapped reads to a BAM file", name="unmapped", helpValue="fname")
    public void setUnmappedFile(String unmappedFilename) {
        this.unmappedFilename=unmappedFilename;
    }
    @Option(desc = "Which tags/attributes to use to determine the best input (comma-delimited, +/- determine order, MAPQ allowed)", name="tags", defaultValue="AS+,NM-")
    public void setTags(String vals) {
        String[] ar = vals.split(",");
        tags = new OrderedTag[ar.length];
        for (int i=0; i<ar.length; i++) {
            tags[i] = new OrderedTag(ar[i]);
        }
    }

    @Option(desc = "Drop all reads where the best source cannot be determined", name="no-ties")
    public void setNoTies(boolean noTies) {
        this.noTies = noTies;
    }

    @Option(desc = "Force output to be flagged as unsorted", name="unsorted")
    public void setUnsorted(boolean unsorted) {
        this.unsorted = unsorted;
    }

    @Option(desc = "Clear the group order flag", name="clear-group")
    public void setClearGroup(boolean clearGroup) {
        this.clearGroup = clearGroup;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Option(desc = "Allow orphaned pairs (for paired-end reads)", name="allow-orphan")
    public void setAllowOrphans(boolean allowOrphans) {
        this.allowOrphans = allowOrphans;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (inputs == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }

        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }
        
        SamReader[] readers = new SamReader[inputs.length];
        FileChannel channel = null;
        
        final int[] inputCounts = new int[inputs.length+1];
        int unmapped = 0;

        List<Iterator<SAMRecord>> iterators = new ArrayList<Iterator<SAMRecord>>();

        // setup inputs
        for (int i=0; i<inputs.length; i++) { 
            if (inputs[i].equals("-")) {
                readers[i] = readerFactory.open(SamInputResource.of(System.in));
                iterators.add(readers[i].iterator());
            } else { 
                File f = new File(inputs[i]);
                FileInputStream fis = new FileInputStream(f);
                readers[i] = readerFactory.open(SamInputResource.of(fis));

                if (channel == null) {
                    // try to have a progress monitor...
                    channel = fis.getChannel();
                    iterators.add(ProgressUtils.getIterator(inputs[i], readers[i].iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
                      @Override
                      public String msg(SAMRecord current) {
                          String s = "";
                          for (int i=0; i<inputs.length; i++) {
                              if (i != 0) {
                                  s += ", ";
                              }
                              s+= inputs[i]+":"+inputCounts[i];
                          }
                          s+= ", ties:" +inputCounts[inputs.length];
                          return s;
                      }}, new CloseableFinalizer<SAMRecord>()));
                } else {
                    iterators.add(readers[i].iterator());
                }
            }
        }

        // setup outputs
        SAMFileWriterFactory factory = new SAMFileWriterFactory();
        SAMFileWriter[] writers = new SAMFileWriter[outputs.length];
        SAMFileWriter unmappedWriter = null;
        if (unmappedFilename != null) {
            SAMFileHeader header = readers[0].getFileHeader().clone();
            if (unsorted) {
                header.setSortOrder(SortOrder.unsorted);
            }
            SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-best", header);
            List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
            pgRecords.add(0, pg);
            header.setProgramRecords(pgRecords);

            if (unmappedFilename.equals("-")) {
                unmappedWriter = factory.makeBAMWriter(header, true, new BufferedOutputStream(System.out));
            } else {
                unmappedWriter = factory.makeBAMWriter(header, true, new File(unmappedFilename));
            }
        }


// TODO: Fix tmp outputs...
//        if (tmpDir != null) {
//            factory.setTempDirectory(new File(tmpDir));
//        } else if (outfile == null || outfile.getParent() == null) {
//            factory.setTempDirectory(new File(".").getCanonicalFile());
//        } else if (outfile != null) {
//            factory.setTempDirectory(outfile.getParentFile());
//        }

        
        for (int i=0; i<outputs.length; i++) {
            SAMFileHeader header = readers[i].getFileHeader().clone();
            if (unsorted) {
                header.setSortOrder(SortOrder.unsorted);
                header.setGroupOrder(GroupOrder.none);
            }
            if (clearGroup) {
            	header.setGroupOrder(GroupOrder.none);
            }
            SAMProgramRecord pg = BamHeaderUtils.buildSAMProgramRecord("bam-best", header);
            List<SAMProgramRecord> pgRecords = new ArrayList<SAMProgramRecord>(header.getProgramRecords());
            pgRecords.add(0, pg);
            header.setProgramRecords(pgRecords);
    
            if (outputs[i].equals("-")) {
                writers[i] = factory.makeBAMWriter(header, true, new BufferedOutputStream(System.out));
            } else {
                writers[i] = factory.makeBAMWriter(header, true, new File(outputs[i]));
            }
        }
        
        SAMRecord[] buffer = new SAMRecord[iterators.size()];
        
        // Pre-load the buffer with one read
        boolean hasNext = true;
        int i = 0;
        for (Iterator<SAMRecord> it: iterators) {
            if (!it.hasNext()) {
                hasNext = false;
            } else {
                buffer[i++] = it.next();
            }
        }
        
        while (hasNext) {
            String currentReadName = null;
            int bestIdx = -1;
            OrderedTagValues bestValues = null;
            List<SAMRecord> bestList = null;
            boolean tie = false;
            
            int idx = 0;
            for (Iterator<SAMRecord> it: iterators) {
                if (currentReadName == null) {
                    currentReadName = buffer[idx].getReadName();
                    if (verbose) {
                        System.err.println(currentReadName);
                    }
                } else if (!buffer[idx].getReadName().equals(currentReadName)) {
                    System.err.println("Read name mismatch!");
                    System.err.println("Expected: " + currentReadName);
                    System.err.println("Got     : " + buffer[idx].getReadName());
                    System.err.println("Input   : " + inputs[idx]);
                    System.exit(1);
                }
                if (verbose) {
                    System.err.println(inputs[idx]);
                }
                List<SAMRecord> curList = new ArrayList<SAMRecord>();
                curList.add(buffer[idx]);
                buffer[idx] = null;

                while (it.hasNext()) {
                    SAMRecord next = it.next();
                    if (!next.getReadName().equals(currentReadName)) {
                        buffer[idx] = next;
                        break;
                    }
                    if (!it.hasNext()) {
                        hasNext = false;
                    }
                    curList.add(next);
                }
                
                for (SAMRecord read: curList) {
                    if (verbose) {
                        System.err.print(read.getReadName()+"\t"+inputs[idx]+"*\t"+"");
                    }
                    if (!keepRead(read)) {
                        if (verbose) {
                            System.err.println("  -- unmapped");
                        }
                        if (unmappedWriter != null) {
                            unmappedWriter.addAlignment(read);
                        }
                        continue;
                    }

                    OrderedTagValues tagValues = extractTagValues(read);
                    if (verbose) {
                        System.err.print(tagValues);
                    }
                    
                    // if bestValues is null, this returns -1
                    int compareToVal = tagValues.compareTo(bestValues);
                    
                    if (compareToVal < 0) {
                        bestIdx = idx;
                        bestValues = tagValues;
                        bestList = curList;
                        tie = false;
                        if (verbose) {
                            System.err.print(" *best*");
                        }
                    } else if (compareToVal == 0) {
                        // tie goes to the first input, so we don't reset bestIdx
                        // check to see that we aren't in the same as the bestIdx, 
                        // if we have paired reads, they can have the same scores.
                        if (bestIdx != idx) {
                            tie = true;
                            if (verbose) {
                                System.err.print(" // tie");
                            }
                        }
                    }
                    if (verbose) {
                        System.err.println();
                    }
                }
                idx++;
            }

            if (tie) {
                inputCounts[inputs.length]++;
                if (!noTies) {
                    if (writers.length > bestIdx) {
                        for (SAMRecord read: bestList) {
                            writers[bestIdx].addAlignment(read);
                        }
                    }
                }
            } else if (bestIdx > -1) {
                inputCounts[bestIdx]++;
                if (writers.length > bestIdx) {
                    for (SAMRecord read: bestList) {
                        writers[bestIdx].addAlignment(read);
                    }
                }
            } else {
                unmapped++;
            }

            if (verbose) {
                System.err.print(currentReadName+" => ");
                if (tie) {
                    System.err.println("best: tie / "+inputs[bestIdx]);
                } else {
                    if (bestIdx > -1) {
                        System.err.println("best: "+inputs[bestIdx]);
                    } else {
                        System.err.println("best: unmapped");
                    }
                }
            }
        }
        
        if (statsFilename != null) {
            @SuppressWarnings("resource")
			PrintStream stats = new PrintStream(new FileOutputStream(statsFilename));
            for (i=0; i<inputs.length; i++) {
                stats.println(inputs[i]+"\t"+inputCounts[i]);
            }

            stats.println("ambiguous\t"+inputCounts[inputs.length]);
            stats.println("unmapped\t"+unmapped);
            stats.close();
        } else {
            for (i=0; i<inputs.length; i++) {
                System.err.println(inputs[i]+"\t"+inputCounts[i]);
            }

            System.err.println("ambiguous\t"+inputCounts[inputs.length]);
            System.err.println("unmapped\t"+unmapped);
        }
        
        for (SamReader reader:readers) {
            reader.close();
        }        

        for (SAMFileWriter writer: writers) {
            writer.close();
        }
        
        if (unmappedWriter != null) {
            unmappedWriter.close();
        }
    }
    
    private boolean keepRead(SAMRecord read) {
        if (read == null) {
            return false;
        }
        
        if (read.getReadUnmappedFlag()) {
            return false;
        }
        
        if (read.getReadPairedFlag()) {
            if (!allowOrphans && read.getMateUnmappedFlag()) {
                return false;
            }
        }
        return true;
    }

    public OrderedTagValues extractTagValues(SAMRecord read) {
        int[] values = new int[tags.length];
        for (int i=0; i< tags.length; i++) {
            if (tags[i].name.equals("MAPQ")) {
                values[i] = read.getMappingQuality();
            } else {
                Integer val = read.getIntegerAttribute(tags[i].name);
                if (val != null) {
                    values[i] = val;
                } else {
                    values[i] = -1;
                }
            }
        }
        return new OrderedTagValues(tags, values);
    }
    
    public boolean isListEmpty(List<?> items) {
        if (items == null) {
            return true;
        }
        
        for (Object o: items) {
            if (o != null) {
                return false;
            }
        }
        return true;        
    }
}
