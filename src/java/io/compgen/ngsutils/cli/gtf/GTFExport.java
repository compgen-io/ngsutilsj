package io.compgen.ngsutils.cli.gtf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.annotation.AnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeAnnotation;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Command(name="gtf-export", desc="Export gene annotations from a GTF file as BED regions", category="gtf")
public class GTFExport extends AbstractOutputCommand {
    private String filename=null;
    private String whitelist = null;
    
    private boolean exportGene = false;
    private boolean exportExon = false;
    private boolean exportIntron = false;
    private boolean exportTSS = false;
    
    private boolean combine = false;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Whitelist of gene names to use", name="whitelist")
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }

    @Option(desc="Combine overlapping exons/introns ", name="combine")
    public void setCombine(boolean val) {
        combine = val;
    }

    @Option(desc="Export whole gene region", name="genes")
    public void setGene(boolean val) {
        exportGene = val;
    }

    @Option(desc="Export transcriptional start site", name="tss")
    public void setTSS(boolean val) {
        exportTSS = val;
    }

    @Option(desc="Export introns", name="introns")
    public void setIntron(boolean val) {
        exportIntron = val;
    }

    @Option(desc="Export exons", name="exons")
    public void setExon(boolean val) {
        exportExon = val;
    }


    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify a GTF file! (- for stdin)");
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
        if (exportTSS) {
            exportCount++;
        }
        if (exportCount != 1) {
            throw new CommandArgumentException("You must specify only one type of region to export (gene, intron, exon, etc)");
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

        for (GenomeAnnotation<GTFGene> ga:IterUtils.wrap(ann.iterator())) {
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
            
            if (exportTSS) {
                int lastStart = -1;
                for (GTFTranscript txpt: gene.getTranscripts()) {
                    if (txpt.getCdsStart() > 0) {
                        if (txpt.getCdsStart() == lastStart) {
                            continue;
                        }
                        if (gene.getStrand() == Strand.PLUS) {
                            writer.write(gene.getRef());
                            writer.write(txpt.getCdsStart());
                            writer.write(txpt.getCdsStart()+3);
                            writer.write(gene.getGeneName());
                            writer.write(0);
                            writer.write(gene.getStrand().toString());
                            writer.eol();
                        } else if (gene.getStrand() == Strand.MINUS) {
                            writer.write(gene.getRef());
                            writer.write(txpt.getCdsStart()-3);
                            writer.write(txpt.getCdsStart());
                            writer.write(gene.getGeneName());
                            writer.write(0);
                            writer.write(gene.getStrand().toString());
                            writer.eol();
                        } else {
                            // this shouldn't happen...
                        }
                        
                        lastStart = txpt.getCdsStart();
                    }
                }
            }

            if (exportExon) {
                if (!combine) {
                    int i=1;
                    for (GenomeSpan exon:gene.getExonRegions()) {
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
                    List<GenomeSpan> exons = gene.getExonRegions();
                    
                    boolean found = true;
                    while (found) {
                        GenomeSpan target = null;
                        GenomeSpan query = null;
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
                            exons.add(new GenomeSpan(target.ref, start, end, target.strand));
                        }
                    }

                    Collections.sort(exons);
                    int i=1;
                    for (GenomeSpan exon:exons) {
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
                    Set<GenomeSpan> introns = new HashSet<GenomeSpan>();
                    for (GTFTranscript txpt:gene.getTranscripts()) {
                        int lastEnd = -1;
                        for (GTFExon exon:txpt.getExons()) {
                            if (lastEnd > -1) {
                                introns.add(new GenomeSpan(gene.getRef(), lastEnd, exon.getStart(), gene.getStrand()));
                            }
                            lastEnd = exon.getEnd();
                        }
                    }
                    int i = 1;
                    for (GenomeSpan intron:introns) {
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
                    List<GenomeSpan> geneRegions = new ArrayList<GenomeSpan>();
                    geneRegions.add(gene.getCoord());
                    boolean found = true;
                    while (found) {
                        found = false;
                        for (GenomeSpan exon:gene.getExonRegions()) {
                            GenomeSpan match = null;
                            for (GenomeSpan gr:geneRegions) {
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
                                    GenomeSpan gr = new GenomeSpan(match.ref, start1, end1, match.strand);
                                    geneRegions.add(gr);
                                }
                                if (start2 < end2) {
                                    GenomeSpan gr = new GenomeSpan(match.ref, start2, end2, match.strand);
                                    geneRegions.add(gr);
                                }
                                
                                found = true;
                                break;
                            }
                        }
                    }
                    Collections.sort(geneRegions);
                    int i=1;
                    for (GenomeSpan intron:geneRegions) {
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
