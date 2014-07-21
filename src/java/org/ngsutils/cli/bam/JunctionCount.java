package org.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;
import net.sf.samtools.SAMSequenceRecord;
import net.sf.samtools.SAMTag;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj junction-count")
@Command(name="junction-count", desc="Counts the number of reads that map to splice junctions", cat="bam")
public class JunctionCount extends AbstractOutputCommand {
    
    protected class JunctionCounter {
        private Set<String> readsR1=new HashSet<String>();
        private Set<String> readsR2=null;
        
        private boolean splitReads = false;
        private String nmTagName = null;
        
        private int editAccR1=0;
        private int editAccR2=0;
        private int editCountR1=0;
        private int editCountR2=0;
        
        public JunctionCounter() {
        }

        public JunctionCounter(boolean splitReads) {
            if (splitReads) {
                readsR2=new HashSet<String>();
                this.splitReads = splitReads;
            }
        }
        public JunctionCounter(String nmTagName) {
            this.nmTagName = nmTagName;
        }

        public JunctionCounter(String nmTagName, boolean splitReads) {
            if (splitReads) {
                readsR2=new HashSet<String>();
                this.splitReads = splitReads;
            }
            this.nmTagName = nmTagName;
        }

        public void addRead(SAMRecord read) {
            if (!splitReads || read.getFirstOfPairFlag()) {
                readsR1.add(read.getReadName());
                if (nmTagName != null) {
                    editAccR1 += getEditDistance(read);
                    editCountR1 += 1;
                }
            } else if (splitReads && !read.getFirstOfPairFlag()) {
                readsR2.add(read.getReadName());
                if (nmTagName != null) {
                    editAccR2 += getEditDistance(read);
                    editCountR2 += 1;
                }
            }
        }
        
        public int getEditDistance(SAMRecord read) {
            Integer nm = read.getIntegerAttribute(nmTagName);
            if (nm!=null) {
                return nm;
            }
            return -1;
        }

        public int getCountR1() {
            return readsR1.size();
        }
        
        public int getCountR2() {
            return readsR2.size();
        }
        
        public double getEditDistanceR1() {
            if (editCountR1 > 0) {
                return (double) editAccR1 / editCountR1;
            }
            return 0;
        }
        
        public double getEditDistanceR2() {
            if (editCountR2 > 0) {
                return (double) editAccR2 / editCountR2;
            }
            return 0;
        }
        
    }
    
    private String samFilename = null;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean editDistance = false;
    private boolean retainedIntrons = false;
    private boolean splitReads = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
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

    @Option(description = "Also count reads for retained introns (default: false)", longName="retrained-introns")
    public void setRetainedIntrons(boolean val) {
        this.retainedIntrons = val;
    }

    @Option(description = "Separate counts by read number (R1/R2) (default: false)", longName="split-reads")
    public void setSplitReads(boolean val) {
        this.splitReads = val;
    }

    @Option(description = "Also report the average edit distance for a reads mapping to a junction (default: false)", longName="edit-distance")
    public void setEditDistance(boolean val) {
        this.editDistance = val;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        SAMFileReader reader = new SAMFileReader(new File(samFilename));
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
//        writer.write_line("## annotation: " + gtfFilename);
        writer.write_line("## library-orientation: " + orient.toString());
        
        writer.write_line("## counts: number of reads for a particular junction");
        if (editDistance) {
            writer.write_line("## counts: average edit-distance for each junction calculated ");
        }
        if (splitReads) {
            writer.write_line("## counts: counts separated by read number ");
        }
        if (retainedIntrons) {
            writer.write_line("## counts: number of reads retained through a junction into an intron ");
        }

        writer.write("junction", "strand");
        if (splitReads) {
            writer.write("readnum");
        }
        writer.write("count");
        if (editDistance) {
            writer.write("avg-edit-distance");
        }
        writer.eol();
        
        String nmTagName = null;
        

        for (SAMSequenceRecord refRecord: reader.getFileHeader().getSequenceDictionary().getSequences()) {
            if (verbose) {
                System.err.println("Finding junctions for: " + refRecord.getSequenceName());
            }
            
            SAMRecordIterator it = reader.query(refRecord.getSequenceName(), 0, refRecord.getSequenceLength(), true);
            SortedMap<GenomeRegion, JunctionCounter> counters = new TreeMap<GenomeRegion, JunctionCounter>();
            
            while (it.hasNext()) {
                SAMRecord read = it.next();
                if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag()) {
                    // skip all secondary / duplicate / unmapped reads
                    continue;
                }
                if (editDistance && nmTagName == null) {
                    Integer nm = read.getIntegerAttribute(SAMTag.NM.name());
                    if (nm != null) {
                        nmTagName = SAMTag.NM.name();
                    } else {
                        nm = read.getIntegerAttribute("nM");
                        if (nm != null) {
                            nmTagName = "nM";
                        } else {
                            it.close();
                            reader.close();
                            throw new RuntimeException("Your BAM file must have the 'NM' tag present to track edit-distance!");
                        }
                    }
                }
                
                if (read.getAlignmentBlocks().size() > 1) {
                    int last_end = -1;
                    for (GenomeRegion flank: ReadUtils.getJunctionFlankingRegions(read, orient)) {
                        if (last_end != -1) {
                            GenomeRegion junction = new GenomeRegion(read.getReferenceName(), last_end, flank.start, flank.strand);
                            
                            if (!counters.containsKey(junction)) {
                                counters.put(junction, new JunctionCounter(nmTagName, splitReads));
                            }
                            
                            counters.get(junction).addRead(read);
                        }
                        last_end = flank.end;
                    }
                }
            }
            it.close();

            if (verbose) {
                System.err.println("                found: " + counters.size());
            }
            
            for (GenomeRegion junc: counters.keySet()) {
                writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                writer.write(""+junc.strand);
                if (splitReads) {
                    writer.write("R1");
                }
                writer.write(counters.get(junc).getCountR1());
                if (editDistance) {
                    writer.write(counters.get(junc).getEditDistanceR1());
                }
                writer.eol();
                
                if (splitReads) {
                    writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                    writer.write(""+junc.strand);
                    if (splitReads) {
                        writer.write("R2");
                    }
                    writer.write(counters.get(junc).getCountR1());
                    if (editDistance) {
                        writer.write(counters.get(junc).getEditDistanceR1());
                    }
                    writer.eol();
                }
            }
            
//            if (retainedIntrons) {
//                for (GenomeRegion junc: counters.keySet()) {
//                    
//                }
//            }
        }

        writer.close();
        reader.close();
    }
}

