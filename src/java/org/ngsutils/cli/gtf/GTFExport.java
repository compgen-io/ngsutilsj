package org.ngsutils.cli.gtf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.AnnotationSource;
import org.ngsutils.annotation.GTFAnnotationSource;
import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.annotation.GenomeAnnotation;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;

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
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Whitelist of gene names to use", longName="whitelist", defaultToNull=true)
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
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
    
        TabWriter writer = new TabWriter();

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

        for (GenomeAnnotation<GTFGene> ga:ann.allAnnotations()) {
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
                int i=1;
                for (GenomeRegion exon:gene.getExons()) {
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

            if (exportIntron) {
                List<GenomeRegion> geneRegions = new ArrayList<GenomeRegion>();
                geneRegions.add(gene.toRegion());
                boolean found = true;
                while (found) {
//                    System.err.println("Regions: ");
//                    for (GenomeRegion gr:geneRegions) {
//                        System.err.println("  "+gr);
//                    }
                    
                    found = false;
                    for (GenomeRegion exon:gene.getExons()) {
//                        System.err.print("Exon: "+exon+" ");
                        GenomeRegion match = null;
                        for (GenomeRegion gr:geneRegions) {
                            if (gr.contains(exon)) {
                                match = gr;
                                break;
                            }
                        }
                        if (match!=null) {
//                            System.err.println("Found!");
                            geneRegions.remove(match);
                            
                            int start1 = match.start;
                            int end1 = exon.start;

                            int start2 = exon.end;
                            int end2 = match.end;
                            
//                            System.err.println("  start/end 1: "+start1+"/"+end1);
                            if (start1 < end1) {
                                GenomeRegion gr = new GenomeRegion(match.ref, start1, end1, match.strand);
//                                System.err.println("  Adding :" + gr) ;
                                geneRegions.add(gr);
                            }
//                            System.err.println("  start/end 2: "+start2+"/"+end2);
                            if (start2 < end2) {
                                GenomeRegion gr = new GenomeRegion(match.ref, start2, end2, match.strand);
//                                System.err.println("  Adding :" + gr) ;
                                geneRegions.add(gr);
                            }
                            
                            found = true;
                            break;
//                        } else {
//                            System.err.println("Clear!");
//
                        }
                    }
                }

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
        
        writer.close();
    }
}
