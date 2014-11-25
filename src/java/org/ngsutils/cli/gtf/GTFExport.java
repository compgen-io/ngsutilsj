package org.ngsutils.cli.gtf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.AnnotationSource;
import org.ngsutils.annotation.GenomeAnnotation;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.annotation.GTFAnnotationSource;
import org.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import org.ngsutils.support.IterUtils;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj gtf-export")
@Command(name="gtf-export", desc="Export gene annotations from a GTF file as BED regions", cat="gtf")
public class GTFExport extends AbstractOutputCommand {
    private String filename=null;
    private String whitelist = null;
    
    private boolean exportGene = false;
    private boolean exportExon = false;
    private boolean exportIntron = false;
    
    private boolean combine = false;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Whitelist of gene names to use", longName="whitelist", defaultToNull=true)
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }

    @Option(description = "Combine overlapping exons/introns ", longName="combine")
    public void setCombine(boolean val) {
        combine = true;
    }

    @Option(description = "Export whole gene region", longName="genes")
    public void setGene(boolean val) {
        exportGene = true;
    }

    @Option(description = "Export introns", longName="introns")
    public void setIntron(boolean val) {
        exportIntron = true;
    }

    @Option(description = "Export exons", longName="exons")
    public void setExon(boolean val) {
        exportExon = true;
    }


    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filename == null) {
            throw new NGSUtilsException("You must specify a GTF file! (- for stdin)");
        }
        
        int exportCount = 0;
        if (exportGene) {
            exportCount++;
        }
        if (exportIntron) {
            exportCount++;
        }
        if (exportExon) {
            exportCount++;
        }
        if (exportCount != 1) {
            throw new NGSUtilsException("You must specify only one type of region to export (gene, intron, exon, etc)");
        }

        if (verbose && combine) {
            System.err.println("Combining overlapping regions");
        }

        TabWriter writer = new TabWriter(out);

        Set<String> whitelistSet = null;
        if (whitelist!=null) {
            if (verbose) {
                System.err.print("Reading whitelist: "+whitelist);
            }
            
            whitelistSet = new HashSet<String>();
            
            if (new File(whitelist).exists()) {
                for (final String line : new StringLineReader(whitelist)) {
                    whitelistSet.add(StringUtils.strip(line));
                }
            } else {
                for (String gene: whitelist.split(",")) {
                    whitelistSet.add(gene);
                }
            }
            
            if (verbose) {
                System.err.println(" [done]");
            }
        }
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+filename);
        }

        AnnotationSource<GTFGene> ann = new GTFAnnotationSource(filename);
        
        if (verbose) {
            System.err.println(" [done]");
        }

        for (GenomeAnnotation<GTFGene> ga:IterUtils.wrapIterator(ann.iterator())) {
            GTFGene gene = ga.getValue();
            if (whitelistSet != null) {
                if (!whitelistSet.contains(gene.getGeneName())) {
                    continue;
                }
            }
            if (exportGene) {
                writer.write(gene.getRef());
                writer.write(gene.getStart());
                writer.write(gene.getEnd());
                writer.write(gene.getGeneName());
                writer.write(0);
                writer.write(gene.getStrand().toString());
                writer.eol();
            }

            if (exportExon) {
                if (!combine) {
                    int i=1;
                    for (GenomeRegion exon:gene.getExonRegions()) {
                        writer.write(exon.ref);
                        writer.write(exon.start);
                        writer.write(exon.end);
                        writer.write(gene.getGeneName()+"/exon-"+i);
                        writer.write(0);
                        writer.write(exon.strand.toString());
                        writer.eol();
                        i++;
                    }
                } else {
                    // combine overlapping exons
                    List<GenomeRegion> exons = gene.getExonRegions();
                    
                    boolean found = true;
                    while (found) {
                        GenomeRegion target = null;
                        GenomeRegion query = null;
                        found = false;
                        
                        for (int i=0; i < exons.size() && !found; i++) {
                            target = exons.get(i);
                            for (int j=i+1; j< exons.size() && !found; j++) {
                                query = exons.get(j);
                                if (target.overlaps(query)) {
                                    found = true;
                                }
                            }
                        }
                        if (found) {
                            exons.remove(target);
                            exons.remove(query);

                            int start = Math.min(target.start,  query.start);
                            int end = Math.max(target.end,  query.end);
                            exons.add(new GenomeRegion(target.ref, start, end, target.strand));
                        }
                    }

                    Collections.sort(exons);
                    int i=1;
                    for (GenomeRegion exon:exons) {
                        writer.write(exon.ref);
                        writer.write(exon.start);
                        writer.write(exon.end);
                        writer.write(gene.getGeneName()+"/exon-"+i);
                        writer.write(0);
                        writer.write(exon.strand.toString());
                        writer.eol();
                        i++;
                    }
                }
            }

            if (exportIntron) {
                if (!combine) {
                    Set<GenomeRegion> introns = new HashSet<GenomeRegion>();
                    for (GTFTranscript txpt:gene.getTranscripts()) {
                        int lastEnd = -1;
                        for (GTFExon exon:txpt.getExons()) {
                            if (lastEnd > -1) {
                                introns.add(new GenomeRegion(gene.getRef(), lastEnd, exon.getStart(), gene.getStrand()));
                            }
                            lastEnd = exon.getEnd();
                        }
                    }
                    int i = 1;
                    for (GenomeRegion intron:introns) {
                        writer.write(intron.ref);
                        writer.write(intron.start);
                        writer.write(intron.end);
                        writer.write(gene.getGeneName()+"/intron-"+i);
                        writer.write(0);
                        writer.write(intron.strand.toString());
                        writer.eol();
                        i++;
                    }
                } else {
                    // Look for introns that don't overlap *any* exons
                    List<GenomeRegion> geneRegions = new ArrayList<GenomeRegion>();
                    geneRegions.add(gene.getCoord());
                    boolean found = true;
                    while (found) {
                        found = false;
                        for (GenomeRegion exon:gene.getExonRegions()) {
                            GenomeRegion match = null;
                            for (GenomeRegion gr:geneRegions) {
                                if (gr.overlaps(exon)) {
                                    match = gr;
                                    break;
                                }
                            }
                            if (match!=null) {
                                geneRegions.remove(match);
                                
                                int start1 = match.start;
                                int end1 = exon.start;
    
                                int start2 = exon.end;
                                int end2 = match.end;
                                
                                if (start1 < end1) {
                                    GenomeRegion gr = new GenomeRegion(match.ref, start1, end1, match.strand);
                                    geneRegions.add(gr);
                                }
                                if (start2 < end2) {
                                    GenomeRegion gr = new GenomeRegion(match.ref, start2, end2, match.strand);
                                    geneRegions.add(gr);
                                }
                                
                                found = true;
                                break;
                            }
                        }
                    }
                    Collections.sort(geneRegions);
                    int i=1;
                    for (GenomeRegion intron:geneRegions) {
                        writer.write(intron.ref);
                        writer.write(intron.start);
                        writer.write(intron.end);
                        writer.write(gene.getGeneName()+"/intron-"+i);
                        writer.write(0);
                        writer.write(intron.strand.toString());
                        writer.eol();
                        i++;
                    }
                }
            }
        }
        
        writer.close();
    }
}
