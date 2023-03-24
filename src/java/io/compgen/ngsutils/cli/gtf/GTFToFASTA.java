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
import io.compgen.ngsutils.annotation.CodingSequence;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeAnnotation;
import io.compgen.ngsutils.fasta.FastaReader;

@Command(name="gtf-tofasta", desc="Export transcript/protein sequences as FASTA files", category="gtf")
public class GTFToFASTA extends AbstractOutputCommand {
    private String[] filenames=null;
    private String includeList = null;
    private String excludeList = null;
    private List<String> requiredTags = null;

    private boolean codingOnly = false;
    private boolean nonCodingOnly = false;

    private boolean protein = false;
    private boolean full = false;
        
    @UnnamedArg(name = "FASTA GTF")
    public void setFilenames(String[] filenames) {
        this.filenames = filenames;
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

    @Option(desc="List of gene names to exclude (filename or comma-separated list)", name="exclude")
    public void setExcludeList(String excludeList) {
        this.excludeList = excludeList;
    }

    @Option(desc="List of gene names to include (filename or comma-separated list)", name="include")
    public void setIncludeList(String includeList) {
        this.includeList = includeList;
    }

    @Option(desc="Only export coding genes/transcripts", name="coding-only")
    public void setCoding(boolean val) {
        codingOnly = val;
    }

    @Option(desc="Only export non-coding genes/transcripts", name="noncoding-only")
    public void setNonCoding(boolean val) {
        nonCodingOnly = val;
    }

    @Option(desc="Export protein sequences (default: cDNA)", name="protein")
    public void setProtein(boolean val) {
    	protein = val;
    	full = !val;
    	codingOnly = val;
    }

    @Option(desc="Export full mRNA sequences (all exons) (default: only cDNA)", name="mrna")
    public void setFull(boolean val) {
    	protein = !val;
    	full = val;
    }


    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (filenames == null || filenames.length != 2) {
            throw new CommandArgumentException("You must specify both a genome FASTA file and a GTF file!");
        }
        
        if (codingOnly && nonCodingOnly) {
            throw new CommandArgumentException("You can't specify --coding-only and --noncoding-only");
        }

        if (nonCodingOnly && protein) {
            throw new CommandArgumentException("You can't specify --noncoding-only and --protein");
        }

        if (excludeList!=null && includeList !=null) {
            throw new CommandArgumentException("You can't specify both --include and --exclude");
        }
        
    	if (!new File(this.filenames[0]+".fai").exists()) { 
    		throw new CommandArgumentException("Missing FAI index for: "+this.filenames[0]);
    	}

        
        TabWriter writer = new TabWriter(out);

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
                System.err.println(" Genes: " + StringUtils.join(",", includeListSet));
            }
        }

        Set<String> excludeListSet = null;
        if (excludeList!=null) {
            if (verbose) {
                System.err.print("Reading exclude list: "+excludeList);
            }
            
            excludeListSet = new HashSet<String>();
            
            if (new File(excludeList).exists()) {
                for (final String line : new StringLineReader(excludeList)) {
                	excludeListSet.add(StringUtils.strip(line));
                }
            } else {
                for (String gene: excludeList.split(",")) {
                	excludeListSet.add(gene);
                }
            }
            
            if (verbose) {
                System.err.println(" [done]");
            }
        }

        FastaReader fasta = FastaReader.open(filenames[0]);
        
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+filenames[1]);
        }

        AnnotationSource<GTFGene> gtf = new GTFAnnotationSource(filenames[1], requiredTags);
        
        if (verbose) {
            System.err.println(" [done]");
        }

        
		for (GenomeAnnotation<GTFGene> ann : IterUtils.wrap(gtf.iterator())) {
			if (includeListSet != null && !includeListSet.contains(ann.getValue().getGeneName())) {
				continue;
			}
			if (excludeListSet != null && excludeListSet.contains(ann.getValue().getGeneName())) {
				continue;
			}
			for (GTFTranscript txpt: ann.getValue().getTranscripts(codingOnly, nonCodingOnly)) {
				writer.write_line(">" + ann.getValue().getGeneName()+"|"+ann.getValue().getGeneId()+"|"+txpt.getTranscriptId()+ " " + ann.getValue().getRef()+":"+txpt.getStart()+"-"+txpt.getEnd());
				CodingSequence cds = CodingSequence.buildFromTranscript(txpt, fasta);
				if (protein) {
					writer.write_line(cds.getAA());
				} else if (full) {
					writer.write_line(txpt.getSequence(fasta));
				} else {
					writer.write_line(cds.getCDS());
				}
			}
		}
		writer.close();
    }
}
