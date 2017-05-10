package io.compgen.ngsutils.cli.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GenicRegion;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

@Command(name="annotate-gtf", desc="Annotate GTF gene regions (for tab-delimited text, BED, or BAM input)", doc="Note: Column indexes start at 1.", category="annotation")
public class GTFAnnotate extends AbstractOutputCommand {
    
    private String filename=null;
    private String gtfFilename=null;
    
    private int refCol = -1;
    private int startCol = -1;
    private int endCol = -1;
    private int strandCol = -1;
    private int regionCol = -1;
    private int junctionCol = -1;
    private boolean junctionWithinGene = false;
    
    private int within = 0;
    
    private boolean hasHeader = true;
    private boolean headerComment = false;
    
    private boolean zeroBased = true;
    
    private List<String> outputs = new ArrayList<String>();
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Annotate a novel junction that is spanned by a gene (requires --col-strand)", name="within-gene")
    public void setWithinGene(boolean junctionWithinGene) {
        this.junctionWithinGene = junctionWithinGene;
    }


    @Option(desc = "GTF filename", name="gtf", helpValue="fname")
    public void setGTFFilename(String gtfFilename) {
        this.gtfFilename = gtfFilename;
    }


    @Option(desc="Column of chromosome", defaultText="1", name="col-chrom", defaultValue="-1")
    public void setChromCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.refCol = val - 1;
        } else { 
            this.refCol = -1;
        }
    }

    @Option(desc="Column of start-position (1-based position)", defaultText="2", name="col-start", defaultValue="-1")
    public void setStartCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.startCol = val - 1;
        } else { 
            this.startCol = -1;
        }
    }

    @Option(desc="Column of end-position", defaultText="no end col", name="col-end", defaultValue="-1")
    public void setEndCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.endCol = val - 1;
        } else { 
            this.endCol = -1;
        }
    }

    @Option(desc="Column of strand", defaultText="not strand-specific", name="col-strand", defaultValue="-1")
    public void setStrandCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.strandCol = val - 1;
        } else { 
            this.strandCol = -1;
        }
    }

    @Option(desc="Column of a region", defaultText="not used", name="col-region", defaultValue="-1")
    public void setRegionCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.regionCol = val - 1;
        } else { 
            this.regionCol = -1;
        }
    }

    @Option(desc="Column of a junction", defaultText="not used", name="col-junction", defaultValue="-1")
    public void setJunctionCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.junctionCol = val - 1;
        } else { 
            this.junctionCol = -1;
        }
    }

    @Option(desc="Use BED3 format presets", name="bed3")
    public void setUseBED3(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = -1;
        }
    }

    @Option(desc="Use BED6 format presets", name="bed6")
    public void setUseBED6(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = 5;
        }
    }

    @Option(desc="Add gene_id annotation", name="gene-id")
    public void setGeneId(boolean val) {
        if (val) {
            outputs.add("gene_id");
        }
    }

    @Option(desc="Add genic region annotation", name="genic-region")
    public void setGenicRegion(boolean val) {
        if (val) {
            outputs.add("genic_region");
        }
    }

    @Option(desc="Add gene_name annotation", name="gene-name")
    public void setGeneName(boolean val) {
        if (val) {
            outputs.add("gene_name");
        }
    }

    @Option(desc="Add biotype annotation", name="biotype")
    public void setBioType(boolean val) {
        if (val) {
            outputs.add("biotype");
        }
    }

    @Option(desc="Input file uses one-based coordinates (default is 0-based)", name="one")
    public void setOneBased(boolean val) {
        zeroBased = !val;
    }

    @Option(desc="Input file doesn't have a header row", name="noheader")
    public void setHasHeader(boolean val) {
        hasHeader = !val;
    }

    @Option(desc="The header is the last commented line", name="header-comment")
    public void setHeaderComment(boolean val) {
        headerComment = val;
    }


    @Option(desc="Region can be within [value] bp of the genomic range (requires start and end columns)", name="within", defaultValue="0")
    public void setWithin(int val) {
        this.within = val;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (gtfFilename == null) {
            throw new CommandArgumentException("You must specify a GTF file!");
        }
        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input file! (- for stdin)");
        }
        
        if (refCol == -1 && startCol == -1 && regionCol == -1 && junctionCol == -1) {
            // set the defaults if nothing is specified
            refCol = 0;
            startCol = 1;
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## gtf-annotations: " + gtfFilename);
        
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+gtfFilename);
        }

        GTFAnnotationSource ann = new GTFAnnotationSource(gtfFilename);
        if (verbose) {
            System.err.println(" [done]");
        }
        
        boolean first = true;
        String lastline = null;
        int colNum = -1;
        for (String line: new StringLineReader(filename)) {
            if (line == null || line.length() == 0) {
                continue;
            }
            try {
                if (line.charAt(0) == '#') {
                    if (lastline != null) {
                        writer.write_line(lastline);
                    }
                    lastline = line;
                    continue;
                }
                
                if (lastline!=null) {
                    if (headerComment && hasHeader) {
                        String[] cols = lastline.split("\\t", -1);
                        colNum = cols.length;
                        writer.write(cols);
                        if (outputs.size()>0) {
                            for (String output: outputs) {
                                if (output.equals("biotype") && !ann.provides("biotype")) {
                                    continue;
                                }
                                writer.write(output);
                            }
                        } else {
                            writer.write("gene_id");
                            writer.write("gene_name");
                            if (ann.provides("biotype")) {
                                writer.write("biotype");                    
                            }
                        }
                        writer.eol();
                        first = false;
                    } else {
                        writer.write_line(lastline);
                    }
                    
                    lastline = null;
                }
                
                String[] cols = line.split("\\t", -1);
                writer.write(cols);
                if (hasHeader && first) {
                    first = false;
                    colNum = cols.length;
                    
                    if (outputs.size()>0) {
                        for (String output: outputs) {
                            if (output.equals("biotype") && !ann.provides("biotype")) {
                                continue;
                            }
                            writer.write(output);
                        }
                    } else {
                        writer.write("gene_id");
                        writer.write("gene_name");
                        if (ann.provides("biotype")) {
                            writer.write("biotype");                    
                        }
                    }
                    
                    writer.eol();
                    continue;
                }
                
                for (int i=cols.length; i<colNum; i++) {
                    writer.write("");
                }
                
                List<GTFGene> annVals;
                GenomeSpan genomeSpan = null;

                if (regionCol > -1) {
                    String region = cols[regionCol];
                    genomeSpan = GenomeSpan.parse(region);
                    annVals = ann.findAnnotation(genomeSpan);
                } else if (junctionCol > -1) {
                    String junction = cols[junctionCol];
                    
                    // This looks only at annotated junctions
                    annVals = ann.findJunction(junction);
                    
                    if (annVals.size() == 0 && junctionWithinGene && strandCol != -1) {
                        String[] spl = junction.split(":");
                        String chrom = spl[0];
                        int start = Integer.parseInt(spl[1].split("-")[0]);
                        int end = Integer.parseInt(spl[1].split("-")[0]);
                        Strand strand = Strand.parse(cols[strandCol]);
                        GenomeSpan span = new GenomeSpan(chrom, start, end, strand);
                        
                        // this will look for genes that include this junction-span
                        annVals = ann.findAnnotation(span);
                    }
                    
                } else {
                    String ref = cols[refCol];
                    int start = Integer.parseInt(cols[startCol])-within;
                    int end = start+within;
                    Strand strand = Strand.NONE;
                    
                    if (!zeroBased && start > 0) {
                        start = start - 1;
                    }
                    
                    if (endCol>-1) { 
                        end = Integer.parseInt(cols[endCol])+within;
                    }
                    
                    if (strandCol>-1) {
                        strand = Strand.parse(cols[strandCol]);
                    }
                    
                    genomeSpan = new GenomeSpan(ref, start, end, strand);
                    annVals = ann.findAnnotation(genomeSpan);
                }

                if (annVals.size() == 0 && genomeSpan != null) {
                    // look for anti-sense annotations
                    GenomeSpan antiSpan = new GenomeSpan(genomeSpan.ref, genomeSpan.start, genomeSpan.end, genomeSpan.strand.getOpposite());
                    annVals = ann.findAnnotation(antiSpan);                    
                }

                
                String[] geneIds = new String[annVals.size()];
                String[] geneNames = new String[annVals.size()];
                String[] bioTypes = new String[annVals.size()];
                String[] regions = new String[annVals.size()];
               
                for (int i=0; i < annVals.size(); i++) {
                    GTFGene gene = annVals.get(i);
                    geneIds[i] = gene.getGeneId();
                    geneNames[i] = gene.getGeneName();
                    bioTypes[i] = gene.getBioType();
                    if (genomeSpan != null) {
                        // determine region annotation based on start/end of the region
                        GenicRegion start = ann.findGenicRegionForPos(genomeSpan.getStartPos(), gene.getGeneId());
                        GenicRegion end = ann.findGenicRegionForPos(genomeSpan.getEndPos(), gene.getGeneId());
                        
                        if (start == end) {
                            regions[i] = start.toString();
                        } else {
                            if (start.isExon != end.isExon && start != GenicRegion.INTERGENIC && end != GenicRegion.INTERGENIC) {
                                if (start.isSense) {
                                    regions[i] = GenicRegion.JUNCTION.toString();
                                } else {
                                    regions[i] = GenicRegion.JUNCTION_ANTI.toString();
                                }
                            } else {
                                if (start.ordinal() < end.ordinal()) {
                                    regions[i] = start.toString();
                                } else {
                                    regions[i] = end.toString();
                                }
                            }
                        }
                    } else {
                        regions[i] = "";
                    }
                }

                if (outputs.size()>0) {
                    for (String output: outputs) {
                        if (output.equals("biotype") && !ann.provides("biotype")) {
                            continue;
                        }
                        switch(output) {
                        case "gene_id":
                            writer.write(StringUtils.join(",", geneIds));
                            break;
                        case "gene_name":
                            if (geneNames.length > 0) {
                                writer.write(StringUtils.join(",", geneNames));
                            } else {
                                // output *something* if the gene name isn't known.
                                writer.write(StringUtils.join(",", geneIds));
                            }
                            break;
                        case "biotype":
                            writer.write(StringUtils.join(",", bioTypes));
                            break;
                        case "genic_region":
                            writer.write(StringUtils.join(",", regions));

                            break;
                        }
                    }
                } else {
                    writer.write(StringUtils.join(",", geneIds));
                    writer.write(StringUtils.join(",", geneNames));
                    if (ann.provides("biotype")) {
                        writer.write(StringUtils.join(",", bioTypes));
                    }
                }
    
                writer.eol();
            } catch (Exception ex) {
                System.err.println("ERROR processing line: "+line);
                System.err.println(ex);
                ex.printStackTrace(System.err);
                throw(ex);
            }
        }

        writer.close();
    }
}
