package io.compgen.ngsutils.cli.gtf;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

@Command(name="gtf-geneinfo", desc="Calculate information about genes (based on GTF model)", category="gtf", experimental=true)
public class GeneExport extends AbstractOutputCommand {
    private String filename=null;
    private String includeList = null;
    
    private List<String> requiredTags = null;
    private boolean exportGenomicSize = false;
    private boolean exportTranscriptSize = false;
    private boolean exportMaxIntron = false;
    
    private boolean codingOnly = false;
    private boolean nonCodingOnly = false;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="List of required tag annotations (comma-separated list)", name="tag", allowMultiple=true)
    public void setRequiredTags(String requiredTags) {
    	if (this.requiredTags == null) {
    		this.requiredTags = new ArrayList<String>();
    	}
    	for (String s:requiredTags.split(",")) {
    		this.requiredTags.add(s);
    	}
    }


    @Option(desc="List of gene names to include", name="include")
    public void setIncludeList(String includeList) {
        this.includeList = includeList;
    }

    @Option(desc="Export the maximum genomic size (unspliced)", name="size-genomic")
    public void setExportGenomicSize(boolean val) {
        exportGenomicSize = val;
    }

    @Option(desc="Export the maximum codingOnly size (spliced)", name="size-transcript")
    public void setExportTranscriptSize(boolean val) {
        exportTranscriptSize = val;
    }

    @Option(desc="Only export coding genes/transcripts", name="coding-only")
    public void setCoding(boolean val) {
        codingOnly = val;
    }

    @Option(desc="Only export non-coding genes/transcripts", name="noncoding-only")
    public void setNonCoding(boolean val) {
        nonCodingOnly = val;
    }

    @Option(desc="Export the size of the longest intron", name="longest-intron")
    public void setExportMaxIntron(boolean val) {
        exportMaxIntron = val;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify a GTF file! (- for stdin)");
        }

        if (codingOnly && nonCodingOnly) {
            throw new CommandArgumentException("You can't specify --coding-only and --noncoding-only");
        }

        if (
                !exportGenomicSize &&
                !exportTranscriptSize &&
                !exportMaxIntron
            ) {
            throw new CommandArgumentException("You must a value to export!");
        }
            
        
        TabWriter writer = new TabWriter(out);
        writer.write("gene_id", "gene_name");
        if (exportGenomicSize) {
            writer.write("genome_size");
        }
        if (exportTranscriptSize) {
            writer.write("transcript_size");
        }
        if (exportMaxIntron) {
            writer.write("max_intron");
        }
        
        writer.eol();

        Set<String> includeListSet = null;
        if (includeList!=null) {
            if (verbose) {
                System.err.print("Reading include list: "+includeList);
            }
            
            includeListSet = new HashSet<String>();
            
            if (new File(includeList).exists()) {
                for (final String line : new StringLineReader(includeList)) {
                    includeListSet.add(StringUtils.strip(line));
                }
            } else {
                for (String gene: includeList.split(",")) {
                    includeListSet.add(gene);
                }
            }
            
            if (verbose) {
                System.err.println(" [done]");
            }
        }
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+filename);
        }

        AnnotationSource<GTFGene> ann = new GTFAnnotationSource(filename, requiredTags);

        if (verbose) {
            System.err.println(" [done]");
        }

        for (GenomeAnnotation<GTFGene> ga:IterUtils.wrap(ann.iterator())) {
            GTFGene gene = ga.getValue();

            if (includeListSet != null) {
                if (!includeListSet.contains(gene.getGeneName())) {
                    continue;
                }
            }
            
            boolean isCoding = false;
            for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                if (txpt.hasCDS()) {
                    isCoding = true;
                    break;
                }
            }
            if (!isCoding) {
                continue;
            }
            
            writer.write(gene.getGeneId(), gene.getGeneName());
            
            if (exportGenomicSize) {
                writer.write(gene.getEnd()-gene.getStart());
            }
            
            if (exportTranscriptSize) {
                int maxLen = 0;
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    int len = 0;
                    for (GTFExon exon: txpt.getExons()) {
                        len += (exon.getEnd()-exon.getStart());
                    }
                    if (len > maxLen) {
                        maxLen = len;
                    }
                }
                writer.write(maxLen);
            }

            if (exportMaxIntron) {
                int maxLen = 0;
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    int lastEnd = -1;
                    for (GTFExon exon: txpt.getExons()) {
                        if (lastEnd != -1) {
                            int intronSize = exon.getStart() - lastEnd;
                            if (intronSize > maxLen) {
                                maxLen = intronSize;
                            }
                        }
                        lastEnd = exon.getEnd();
                    }
                }
                writer.write(maxLen);
            }

            writer.eol();
        }

        writer.close();
    }
}
