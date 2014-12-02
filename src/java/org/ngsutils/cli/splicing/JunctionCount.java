package org.ngsutils.cli.splicing;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.support.ReadUtils;
import org.ngsutils.bam.support.ReadUtils.MappedReadCounter;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj junction-count")
@Command(name="junction-count", desc="Counts the number of reads that map to splice junctions", cat="splicing", experimental=true)
public class JunctionCount extends AbstractOutputCommand {
    private String filename = null;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean editDistance = false;
    private boolean retainedIntrons = false;
    private boolean splitReads = false;
    private int minOverlap = 10;

    private Orientation orient = Orientation.UNSTRANDED;
    
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

    @Option(description = "Also count reads for retained introns (default: false)", longName="retained-introns")
    public void setRetainedIntrons(boolean val) {
        this.retainedIntrons = val;
    }

    @Option(description = "Minimum overlap for retained introns (default: 10)", longName="min-overlap", defaultValue="10")
    public void setMinOverlap(int val) {
        this.minOverlap = val;
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
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
        } else {
            reader = readerFactory.open(new File(filename));
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + filename);
//        writer.write_line("## annotation: " + gtfFilename);
        writer.write_line("## library-orientation: " + orient.toString());
        writer.write_line("## min-overlap: " + minOverlap);
        
        writer.write_line("## counts: junction-spanning");
        if (editDistance) {
            writer.write_line("## counts: edit-distance (NM) ");
        }
        if (splitReads) {
            writer.write_line("## counts: split-reads ");
        }
        if (retainedIntrons) {
            writer.write_line("## counts: retained-introns");
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
        
        int readLength = ReadUtils.getReadLength(reader);
        if (verbose) {
        	System.err.println("Read length: "+readLength);
        }

        for (SAMSequenceRecord refRecord: reader.getFileHeader().getSequenceDictionary().getSequences()) {
            if (verbose) {
                System.err.println("Finding junctions for: " + refRecord.getSequenceName());
            }
            
            SortedMap<GenomeRegion, MappedReadCounter> counters = ReadUtils.findJunctions(reader, refRecord.getSequenceName(), 0, refRecord.getSequenceLength(), orient, minOverlap, editDistance ? "NM": null, splitReads);

            if (verbose) {
                System.err.println("                found: " + counters.size());
            }
            
            SortedSet<GenomeRegion> intronCache = new TreeSet<GenomeRegion>();

            for (GenomeRegion junc: counters.keySet()) {
                writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                writer.write(""+junc.strand);
                if (splitReads) {
                    writer.write("R1");
                }
                writer.write(counters.get(junc).getCountR1());
                if (editDistance) {
                    writer.write(counters.get(junc).getTagMeanR1());
                }
                writer.eol();
                
                if (splitReads) {
                    writer.write(junc.ref+":"+junc.start+"-"+junc.end);
                    writer.write(""+junc.strand);
                    if (splitReads) {
                        writer.write("R2");
                    }
                    writer.write(counters.get(junc).getCountR2());
                    if (editDistance) {
                        writer.write(counters.get(junc).getTagMeanR2());
                    }
                    writer.eol();
                }
                
                if (retainedIntrons) {
                    intronCache.add(new GenomeRegion(junc.ref, junc.start, junc.strand));
                    intronCache.add(new GenomeRegion(junc.ref, junc.end, junc.strand));                	
                }
            }
            
            if (retainedIntrons) {
                if (verbose) {
                    System.err.println("    - looking for retained introns");
                }

                for (GenomeRegion spliceSite: intronCache) {
                    MappedReadCounter counter = new MappedReadCounter(editDistance ? "NM": null, splitReads);
                    for (SAMRecord read: ReadUtils.findOverlappingReads(reader, spliceSite, orient, readLength, minOverlap)) {
                        counter.addRead(read);
                    }
                    if (verbose) {
                        System.err.println(spliceSite+" ("+counter.getCountR1()+")");
                    }

                    writer.write(spliceSite.ref+":"+spliceSite.start+"-"+spliceSite.start);
                    writer.write(""+spliceSite.strand);
                    if (splitReads) {
                        writer.write("R1");
                    }
                    writer.write(counter.getCountR1());
                    if (editDistance) {
                        writer.write(counter.getTagMeanR1());
                    }
                    writer.eol();
                
                    if (splitReads) {
                        writer.write(spliceSite.ref+":"+spliceSite.start+"-"+spliceSite.start);
                        writer.write(""+spliceSite.strand);
                        if (splitReads) {
                            writer.write("R2");
                        }
                        writer.write(counter.getCountR2());
                        if (editDistance) {
                            writer.write(counter.getTagMeanR2());
                        }
                        writer.eol();
                    }
                }
            }
        }

        writer.close();
        reader.close();
    }
}

