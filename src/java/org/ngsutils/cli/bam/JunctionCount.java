package org.ngsutils.cli.bam;

import java.io.File;
import java.io.IOException;
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
    
    private String samFilename = null;
//    private String gtfFilename = null;
    
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
//
//    @Option(description = "GTF annotation file", longName="gtf")
//    public void setGTFFilename(String gtfFilename) {
//        this.gtfFilename = gtfFilename;
//    }

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

    @Option(description = "Separate reads by read number (R1/R2) (default: false)", longName="split-reads")
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
            
            SortedMap<GenomeRegion, Integer> junctionCountsR1 = new TreeMap<GenomeRegion, Integer>();
            SortedMap<GenomeRegion, Integer> junctionCountsR2 = new TreeMap<GenomeRegion, Integer>();
            SortedMap<GenomeRegion, Integer> junctionEditAccR1 = new TreeMap<GenomeRegion, Integer>();
            SortedMap<GenomeRegion, Integer> junctionEditAccR2 = new TreeMap<GenomeRegion, Integer>();
            
            while (it.hasNext()) {
                SAMRecord read = it.next();
                if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag()) {
                    // skip all secondary / duplicate / unmapped reads
                    continue;
                }
                
                if (read.getAlignmentBlocks().size() > 1) {
                    int last_end = -1;
                    for (GenomeRegion flank: ReadUtils.getJunctionFlankingRegions(read, orient)) {
                        if (last_end != -1) {
                            GenomeRegion junction = new GenomeRegion(read.getReferenceName(), last_end, flank.start, flank.strand);
                            
                            if (!junctionCountsR1.containsKey(junction)) {
                                if (editDistance) {
                                    junctionEditAccR1.put(junction, 0);
                                }
                                junctionCountsR1.put(junction,  0);
                                if (splitReads) {
                                    junctionCountsR2.put(junction,  0);
                                    if (editDistance) {
                                        junctionEditAccR2.put(junction, 0);
                                    }                                    
                                }
                            }

                            if (editDistance) {
                                if (nmTagName == null) {
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

                                Integer nm = read.getIntegerAttribute(nmTagName);

                                if (nm!=null) {
                                    if (!splitReads || read.getFirstOfPairFlag()) {
                                        junctionEditAccR1.put(junction,  junctionEditAccR1.get(junction)+nm);
                                    } else {
                                        junctionEditAccR2.put(junction,  junctionEditAccR2.get(junction)+nm);
                                    }
                                }
                            }

                            if (!splitReads || read.getFirstOfPairFlag()) {
                                junctionCountsR1.put(junction,  junctionCountsR1.get(junction)+1);
                            } else {
                                junctionCountsR2.put(junction,  junctionCountsR2.get(junction)+1);
                            }
                        }
                        last_end = flank.end;
                    }
                }
            }
            it.close();

            if (verbose) {
                System.err.println("                found: " + junctionCountsR1.size());
            }
            
            for (GenomeRegion junc: junctionCountsR1.keySet()) {
                writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                writer.write(""+junc.strand);
                if (splitReads) {
                    writer.write("R1");
                }
                Integer count = junctionCountsR1.get(junc);
                writer.write(count);
                if (editDistance) {
                    if (count > 0) {
                        writer.write((double)junctionEditAccR1.get(junc) / count);
                    } else {
                        writer.write("0");
                    }
                }
                writer.eol();
                
                if (splitReads) {
                    writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                    writer.write(""+junc.strand);
                    if (splitReads) {
                        writer.write("R2");
                    }
                    count = junctionCountsR2.get(junc);
                    writer.write(count);
                    if (editDistance) {
                        if (count > 0) {
                            writer.write((double)junctionEditAccR2.get(junc) / count);
                        } else {
                            writer.write("0");
                        }
                    }
                    writer.eol();
                }
            }
            junctionCountsR1.clear();
            junctionCountsR2.clear();
            junctionEditAccR1.clear();
            junctionEditAccR2.clear();
        }

        writer.close();
        reader.close();
    }
}

