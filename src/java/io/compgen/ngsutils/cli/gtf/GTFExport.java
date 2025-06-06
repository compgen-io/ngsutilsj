package io.compgen.ngsutils.cli.gtf;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
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

@Command(name="gtf-export", desc="Export gene annotations from a GTF file as BED regions", category="gtf")
public class GTFExport extends AbstractOutputCommand {
    private String filename=null;
    private List<String> requiredTags = null;
    private String includeList = null;
    private String excludeList = null;
    
    private boolean exportGene = false;
    private boolean exportTranscript = false;
    private boolean exportExon = false;
    private boolean exportIntron = false;
    private boolean codingOnly = false;
    private boolean nonCodingOnly = false;
    
    private boolean exportUTR3 = false;
    private boolean exportUTR5 = false;
    private boolean exportTSS = false;
    private boolean exportTLSS = false;
    private boolean exportJunctions = false;
    private boolean exportDonors = false;
    private boolean exportAcceptors = false;
    private boolean exportORF = false;
    
    private boolean combine = false;
    private boolean useGeneId = false;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="List of gene names to exclude (filename or comma-separated list)", name="exclude")
    public void setExcludeList(String excludeList) {
        this.excludeList = excludeList;
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

    @Option(desc="List of gene names to include (filename or comma-separated list)", name="include")
    public void setIncludeList(String includeList) {
        this.includeList = includeList;
    }

    @Option(desc="Combine overlapping exons/introns. For TSS, export at most one TSS per gene. ", name="combine")
    public void setCombine(boolean val) {
        combine = val;
    }

    @Option(desc="Only export coding genes/transcripts", name="coding-only")
    public void setCoding(boolean val) {
        codingOnly = val;
    }

    @Option(desc="Only export non-coding genes/transcripts", name="noncoding-only")
    public void setNonCoding(boolean val) {
        nonCodingOnly = val;
    }

    @Option(desc="Export whole gene region (by geneid)", name="geneid")
    public void setUseGeneId(boolean val) {
        exportGene = val;
        useGeneId = val;
    }

    @Option(desc="Export whole gene region (by name)", name="genes")
    public void setGene(boolean val) {
        exportGene = val;
    }

    @Option(desc="Export transcript regions", name="transcripts")
    public void setTranscript(boolean val) {
    	exportTranscript = val;
    }

    @Option(desc="Export splice junction donor sites", name="donors")
    public void setDonors(boolean val) {
        exportDonors = val;
    }

    @Option(desc="Export splice junction acceptor sites", name="acceptors")
    public void setAcceptors(boolean val) {
        exportAcceptors = val;
    }

    @Option(desc="Export annotated splice junctions", name="junctions")
    public void setJunctions(boolean val) {
        exportJunctions = val;
    }

    @Option(desc="Export transcriptional start site", name="tss")
    public void setTSS(boolean val) {
        exportTSS = val;
    }

    @Option(desc="Export translational stop site/stop codon", name="tlss")
    public void setTLSS(boolean val) {
        exportTLSS = val;
    }

    @Option(desc="Export 5' UTR", name="utr5")
    public void setUTR5(boolean val) {
        exportUTR5 = val;
    }

    @Option(desc="Export 3' UTR", name="utr3")
    public void setUTR3(boolean val) {
        exportUTR3 = val;
    }


    @Option(desc="Export introns", name="introns")
    public void setIntron(boolean val) {
        exportIntron = val;
    }

    @Option(desc="Export exons", name="exons")
    public void setExon(boolean val) {
        exportExon = val;
    }

    @Option(desc="Export ORF", name="orf")
    public void setORF(boolean val) {
        exportORF = val;
    }


    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify a GTF file! (- for stdin)");
        }
        
        if (codingOnly && nonCodingOnly) {
            throw new CommandArgumentException("You can't specify --coding-only and --noncoding-only");
        }

        int exportCount = 0;
        if (exportGene) {
            exportCount++;
        }
        if (exportTranscript) {
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
        if (exportTLSS) {
            exportCount++;
        }
        if (exportJunctions) {
            exportCount++;
        }
        if (exportDonors) {
            exportCount++;
        }
        if (exportAcceptors) {
            exportCount++;
        }
        if (exportUTR5) {
            exportCount++;
        }
        if (exportUTR3) {
            exportCount++;
        }
        if (exportORF) {
            exportCount++;
        }
        if (exportCount != 1) {
            throw new CommandArgumentException("You must specify only one type of region to export at a time (gene, intron, exon, etc)");
        }

        if (verbose && combine) {
            System.err.println("Combining overlapping regions");
        }

        if (excludeList!=null && includeList !=null) {
            throw new CommandArgumentException("You can't specify both --include and --exclude");
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
            if (excludeListSet != null) {
                if (excludeListSet.contains(gene.getGeneName())) {
                    continue;
                }
            }
            if (exportGene) {
            	// For genes... if coding-only is set, only return genes that have a CDS.
            	//              if noncoding-only is set, only return genes that lack a CDS.
            	//
            	// For all other regions, this is region-specific (coding/non-coding exons, etc...)
            	//
                if (codingOnly || nonCodingOnly) {
                    boolean isCoding = false;
                    for (GTFTranscript txpt: gene.getTranscripts(true, false)) {
                        if (txpt.hasCDS()) {
                            isCoding = true;
                            break;
                        }
                    }
                    if (codingOnly && !isCoding) {
                        continue;
                    }
                    if (nonCodingOnly && isCoding) {
                        continue;
                    }
                }
                writer.write(gene.getRef());
                writer.write(gene.getStart());
                writer.write(gene.getEnd());
                if (useGeneId) {
                    writer.write(gene.getGeneId());
                } else {
                    writer.write(gene.getGeneName());
                }
                writer.write(0);
                writer.write(gene.getStrand().toString());
                writer.eol();
            }
            if (exportTranscript) {
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
//                    boolean isCoding = false;
//	                if (codingOnly) {
//                        if (txpt.hasCDS()) {
//                            isCoding = true;
//                            break;
//                        }
//                    }
//                    if (!isCoding) {
//                        continue;
//                    }
	                    
	                writer.write(gene.getRef());
	                writer.write(txpt.getStart());
	                writer.write(txpt.getEnd());
                    writer.write(txpt.getTranscriptId());
	                writer.write(0);
	                writer.write(gene.getStrand().toString());
	                writer.eol();
                }
            }
            

            if (exportJunctions) {
                Set<String> junctions = new HashSet<String>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    int lastpos = -1;
                    for (GTFExon exon: txpt.getExons()) {
                        if (lastpos > -1) {
                            String junc = gene.getRef()+":"+lastpos+"-"+exon.getStart();
                            if (!junctions.contains(junc)) {
                                junctions.add(junc);
                                writer.write(junc);
                                writer.eol();
                            }
                        }
                        lastpos = exon.getEnd();
                    }
                }
            }
            
            if (exportDonors) {
                Set<Integer> outs = new HashSet<Integer>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    List<GTFExon> exons = txpt.getExons();
                    if (gene.getStrand().equals(Strand.PLUS)) {
                        for (GTFExon exon: exons.subList(0,exons.size()-1)) {
                            if (!outs.contains(exon.getEnd())) {
                                outs.add(exon.getEnd());
                                writer.write(gene.getRef(), ""+exon.getEnd(), ""+(exon.getEnd()+1), gene.getGeneName(), "0", ""+gene.getStrand());
                                writer.eol();
                            }
                        }
                    } else {
                        for (GTFExon exon: exons.subList(1,exons.size())) {
                            if (!outs.contains(exon.getStart())) {
                                outs.add(exon.getStart());
                                writer.write(gene.getRef(), ""+(exon.getStart()-1), ""+exon.getStart(), gene.getGeneName(), "0", ""+gene.getStrand());
                                writer.eol();
                            }
                        }
                    }
                }
            }

            if (exportAcceptors) {
                Set<Integer> outs = new HashSet<Integer>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    List<GTFExon> exons = txpt.getExons();
                    if (gene.getStrand().equals(Strand.PLUS)) {
                        for (GTFExon exon: exons.subList(1,exons.size())) {
                            if (!outs.contains(exon.getStart())) {
                                outs.add(exon.getStart());
                                writer.write(gene.getRef(), ""+(exon.getStart()-1), ""+exon.getStart(), gene.getGeneName(), "0", ""+gene.getStrand());
                                writer.eol();
                            }
                        }
                    } else {
                        for (GTFExon exon: exons.subList(0,exons.size()-1)) {
                            if (!outs.contains(exon.getEnd())) {
                                outs.add(exon.getEnd());
                                writer.write(gene.getRef(), ""+exon.getEnd(), ""+(exon.getEnd()+1), gene.getGeneName(), "0", ""+gene.getStrand());
                                writer.eol();
                            }
                        }
                    }
                }
            }

            
            if (exportTSS) {
                List<Integer> starts = new ArrayList<Integer>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    if (gene.getStrand() == Strand.PLUS) {
                        if (starts.contains(txpt.getStart())) {
                            continue;
                        }
                        starts.add(txpt.getStart());
                        if (!combine) {
                            writer.write(gene.getRef());
                            writer.write(txpt.getStart());
                            writer.write(txpt.getStart()+1);
//                            if (useGeneId) {
//                                writer.write(gene.getGeneId()+"-"+txpt.getTranscriptId());
//                            } else {
                                writer.write(gene.getGeneName());
//                            }
                            writer.write(0);
                            writer.write(gene.getStrand().toString());
                            writer.eol();
                        }
                    } else if (gene.getStrand() == Strand.MINUS) {
                        if (starts.contains(txpt.getEnd())) {
                            continue;
                        }
                        starts.add(txpt.getEnd());

                        if (!combine) {
                            writer.write(gene.getRef());
                            writer.write(txpt.getEnd()-1);
                            writer.write(txpt.getEnd());
//                            if (useGeneId) {
//                                writer.write(gene.getGeneId()+"-"+txpt.getTranscriptId());
//                            } else {
                            writer.write(gene.getGeneName()+"/"+txpt.getTranscriptId());
//                            }
                            writer.write(0);
                            writer.write(gene.getStrand().toString());
                            writer.eol();
                        }
                    } else {
                    }
                }
                if (combine) {
                    if (gene.getStrand() == Strand.PLUS) {
                        int min = starts.get(0);
                        for (Integer i: starts) {
                            if (i < min) {
                                min = i;
                            }
                        }
                        writer.write(gene.getRef());
                        writer.write(min);
                        writer.write(min+1);
//                        if (useGeneId) {
//                            writer.write(gene.getGeneId());
//                        } else {
                            writer.write(gene.getGeneName());
//                        }
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();

                    } else if (gene.getStrand() == Strand.MINUS) {
                        int max = starts.get(0);
                        for (Integer i: starts) {
                            if (i > max) {
                                max = i;
                            }
                        }

                        writer.write(gene.getRef());
                        writer.write(max-1);
                        writer.write(max);
//                        if (useGeneId) {
//                            writer.write(gene.getGeneId());
//                        } else {
                            writer.write(gene.getGeneName());
//                        }
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    } else {
                    }
                }
            }

            if (exportTLSS) {
            	// translational stop site => stop codon
                Set<Pair<Integer, Integer>> stops = new HashSet<Pair<Integer, Integer>>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    if (!txpt.hasCDS() || gene.getStrand() == Strand.NONE || txpt.getStopCodonStart() == 1) {
                        continue;
                    }

                    Pair<Integer, Integer> stop = new Pair<Integer, Integer>(txpt.getStopCodonStart(), txpt.getStopCodonEnd());
                    
                    stops.add(stop);
                    if (!combine) {
                        writer.write(gene.getRef());
                        writer.write(stop.one);
                        writer.write(stop.two);
                        writer.write(gene.getGeneName()+"/"+txpt.getTranscriptId());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
                if (combine) {
                	for (Pair<Integer, Integer> stop: stops) {
                        writer.write(gene.getRef());
                        writer.write(stop.one);
                        writer.write(stop.two);
                        writer.write(gene.getGeneName());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
            }

            if (exportUTR3) {
                Set<Pair<Integer, Integer>> utrs = new HashSet<Pair<Integer, Integer>>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    if (!txpt.hasCDS() || gene.getStrand() == Strand.NONE) {
                        continue;
                    }

                    if (gene.getStrand().equals(Strand.PLUS) && txpt.getStopCodonEnd() >= txpt.getEnd()) {
                        // 3' UTR is from end of the stop codon to the end of the transcript
                        // require some amount of UTR... (likely an odd transcript)
                    	// if the stop codon is at or after (?) the end of the transcript, there is no 3' UTR
                        continue;
                    } else if (gene.getStrand().equals(Strand.MINUS) && txpt.getStopCodonStart() <= txpt.getStart()) {
                        // 3' UTR is from the start of the transcript to the start of the stop codon
                        // require some amount of UTR... (likely an odd transcript)
                    	// if the stop codon is at or before (?) the start of the transcript, there is no 3' UTR
                        continue;
                    }
                    
                    Pair<Integer, Integer> utr = null;
                    
                    if (gene.getStrand().equals(Strand.PLUS)) {
                        utr = new Pair<Integer, Integer>(txpt.getStopCodonEnd(), txpt.getEnd());
                    } else if (gene.getStrand().equals(Strand.MINUS)) {
                        utr = new Pair<Integer, Integer>(txpt.getStart(), txpt.getStopCodonStart());
                    } else {
                        // should never happen
                        continue;
                    }
                    
                    if (utrs.contains(utr)) {
                        continue;
                    }
                    
                    utrs.add(utr);

                    if (!combine) {
                        writer.write(gene.getRef());
                        writer.write(utr.one);
                        writer.write(utr.two);
                        writer.write(gene.getGeneName()+"/"+txpt.getTranscriptId());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
                if (combine) {
                    int min = -1;
                    int max = -1;
                    
                    for (Pair<Integer, Integer> utr: utrs) {
                        if (min == -1 || utr.one < min) {
                            min = utr.one;
                        }
                        if (max == -1 || utr.two > max) {
                            max = utr.two;
                        }
                    }
                    
                    if (min > -1 && max > -1) {
                        writer.write(gene.getRef());
                        writer.write(min);
                        writer.write(max);
                        writer.write(gene.getGeneName());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
            }
            if (exportUTR5) {
                Set<Pair<Integer, Integer>> utrs = new HashSet<Pair<Integer, Integer>>();
                for (GTFTranscript txpt: gene.getTranscripts(codingOnly, nonCodingOnly)) {
                    if (!txpt.hasCDS() || gene.getStrand() == Strand.NONE) {
                        continue;
                    }
                    if (gene.getStrand().equals(Strand.PLUS) && txpt.getStartCodonStart() <= txpt.getStart()) {
                        // 5' UTR is from start of transcript to the start codon
                        // require some amount of UTR... (likely an odd transcript)
                    	// if the start codon is at or before (?) the start of the transcript, there is no 5' UTR
                        continue;
                    } else if (gene.getStrand().equals(Strand.MINUS) && txpt.getStartCodonEnd() >= txpt.getEnd()) {
                        // 5' UTR is from the end of the start codon to the end of the transcript
                        // require some amount of UTR... (likely an odd transcript)
                    	// if the start codon is at or after (?) the end of the transcript, there is no 5' UTR
                        continue;
                    }
                    
                    Pair<Integer, Integer> utr = null;
                    
                    if (gene.getStrand().equals(Strand.MINUS)) {
                        utr = new Pair<Integer, Integer>(txpt.getStartCodonEnd(), txpt.getEnd());
                    } else if (gene.getStrand().equals(Strand.PLUS)) {
                        utr = new Pair<Integer, Integer>(txpt.getStart(), txpt.getStartCodonStart());
                    } else {
                        // should never happen
                        continue;
                    }
                    
                    if (utrs.contains(utr)) {
                        continue;
                    }
                    
                    utrs.add(utr);

                    if (!combine) {
                        writer.write(gene.getRef());
                        writer.write(utr.one);
                        writer.write(utr.two);
                        writer.write(gene.getGeneName()+"/"+txpt.getTranscriptId());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
                if (combine) {
                    int min = -1;
                    int max = -1;
                    
                    for (Pair<Integer, Integer> utr: utrs) {
                        if (min == -1 || utr.one < min) {
                            min = utr.one;
                        }
                        if (max == -1 || utr.two > max) {
                            max = utr.two;
                        }
                    }
                    
                    if (min > -1 && max > -1) {
                        writer.write(gene.getRef());
                        writer.write(min);
                        writer.write(max);
                        writer.write(gene.getGeneName());
                        writer.write(0);
                        writer.write(gene.getStrand().toString());
                        writer.eol();
                    }
                }
            }

            

            
            if (exportExon) {
                if (!combine) {
                    int i=1;
                    List<GenomeSpan> exonRegions = gene.getExonRegions(codingOnly);
                    if (gene.getStrand().matches(Strand.MINUS)) {
                    	i = exonRegions.size();
                    }
                    for (GenomeSpan exon:exonRegions) {
//                        String name = gene.getGeneName();
//                        if (useGeneId) {
//                            name = gene.getGeneId()+"-"+exon.getParent().getTranscriptId();
//                        }
                        
                        writer.write(exon.ref);
                        writer.write(exon.start);
                        writer.write(exon.end);
                        
                        writer.write(gene.getGeneName()+"/exon-"+i);
                        writer.write(0);
                        writer.write(exon.strand.toString());
                        writer.eol();
                        if (gene.getStrand().matches(Strand.MINUS)) {
                        	i--;
                        } else {
                        	i++;
                        }
                    }
                } else {
                    // combine overlapping exons
                    List<GenomeSpan> exons = gene.getExonRegions(codingOnly);
                    
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
                    if (gene.getStrand().matches(Strand.MINUS)) {
                    	i = exons.size();
                    }
                    for (GenomeSpan exon:exons) {
                        writer.write(exon.ref);
                        writer.write(exon.start);
                        writer.write(exon.end);
                        writer.write(gene.getGeneName()+"/exon-"+i);
                        writer.write(0);
                        writer.write(exon.strand.toString());
                        writer.eol();
                        if (gene.getStrand().matches(Strand.MINUS)) {
                        	i--;
                        } else {
                        	i++;
                        }
                    }
                }
            }

            if (exportIntron) {
                if (!combine) {
                    SortedSet<GenomeSpan> introns = new TreeSet<GenomeSpan>();
                    for (GTFTranscript txpt:gene.getTranscripts(codingOnly, nonCodingOnly)) {
                        int lastEnd = -1;
                        for (GTFExon exon:txpt.getExons()) {
                            if (lastEnd > -1) {
                                introns.add(new GenomeSpan(gene.getRef(), lastEnd, exon.getStart(), gene.getStrand()));
                            }
                            lastEnd = exon.getEnd();
                        }
                    }
                    int i = 1;
                    if (gene.getStrand().matches(Strand.MINUS)) {
                    	i = introns.size();
                    }
                    for (GenomeSpan intron:introns) {
                        writer.write(intron.ref);
                        writer.write(intron.start);
                        writer.write(intron.end);
                        writer.write(gene.getGeneName()+"/intron-"+i);
                        writer.write(0);
                        writer.write(intron.strand.toString());
                        writer.eol();
                        if (gene.getStrand().matches(Strand.MINUS)) {
                        	i--;
                        } else {
                        	i++;
                        }
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
                    if (gene.getStrand().matches(Strand.MINUS)) {
                    	i = geneRegions.size();
                    }
                    for (GenomeSpan intron:geneRegions) {
                        writer.write(intron.ref);
                        writer.write(intron.start);
                        writer.write(intron.end);
                        writer.write(gene.getGeneName()+"/intron-"+i);
                        writer.write(0);
                        writer.write(intron.strand.toString());
                        writer.eol();
                        if (gene.getStrand().matches(Strand.MINUS)) {
                        	i--;
                        } else {
                        	i++;
                        }
                    }
                }
            }
            if (exportORF) {
                // {buf} will be a bitmap of the ORF for each position
                // in the gene.
                //
                // ORF will be 1 | 2 | 3, or rather 1 | 2 | 4
                //
                // 000 -> not coding (UTRs -- introns will maintain ORF to keep track for the next coding base)
                // 001 -> ORF1
                // 010 -> ORF2
                // 100 -> ORF3
                // 011 -> ORF1 and ORF2 (from different transcripts -- this should be rare)
                // etc...
                //
                byte[] buf = new byte[gene.getEnd() - gene.getStart()];
                
                for (GTFTranscript txpt: gene.getTranscripts()) {
                    if (!txpt.hasCDS()) {
                        continue;
                    }
                    int orf = 0;
                    int lastPos = 0;
                    if (gene.getStrand() == Strand.PLUS) {
                        for (GTFExon exon: txpt.getExons()) {
                            // Intron marks the *next* ORF
                            if (orf > 0 && lastPos > 0) {
                                int nextorf = orf;
                                nextorf = nextorf << 1;
                                if (nextorf > 4) {
                                    nextorf = 1;
                                }

                                for (int pos = lastPos; pos < exon.getStart(); pos++) {
                                    buf[pos - gene.getStart()] = (byte) (buf[pos - gene.getStart()] | nextorf);
                                    //buf[pos - gene.getStart()] |= nextorf;
                                }
                            }

                            for (int pos=exon.getStart(); pos<exon.getEnd(); pos++ ) {
                                if (pos == txpt.getStartCodonStart()) {
                                    orf = 1;
                                } else if (pos == txpt.getStopCodonStart()) {
                                    orf = 0;
                                } else if (orf > 0) {
                                    orf = orf << 1;
                                    if (orf > 4) {
                                        orf = 1;
                                    }
                                }
                                if (orf > 0) {
                                    buf[pos - gene.getStart()] = (byte) (buf[pos - gene.getStart()] | orf);
//                                    buf[pos - gene.getStart()] |= orf;
                                }
                            }
                            lastPos = exon.getEnd();
                        }
                    } else if (gene.getStrand() == Strand.MINUS) {
                        for (GTFExon exon: txpt.getExons()) {
                            // Intron marks the *next* ORF
                            if (orf > 0 && lastPos > 0) {
                                int nextorf = orf;
                                for (int pos = lastPos; pos < exon.getStart(); pos++) {
                                    buf[pos - gene.getStart()] = (byte) (buf[pos - gene.getStart()] | nextorf);
//                                    buf[pos - gene.getStart()] |= nextorf;
                                }
                            }

                            for (int pos=exon.getStart(); pos<exon.getEnd(); pos++ ) {
                                if (pos == txpt.getCdsStart()) {
                                    orf = 4;
                                } else if (pos == txpt.getStartCodonEnd()) {
                                    orf = 0;
                                } else if (orf > 0) {
                                    orf = orf >> 1;
                                    if (orf == 0) {
                                        orf = 4;
                                    }
                                }
                                if (orf > 0) {
                                    buf[pos - gene.getStart()] = (byte) (buf[pos - gene.getStart()] | orf);
//                                    buf[pos - gene.getStart()] |= orf;
                                }
                            }
                            lastPos = exon.getEnd();
                        }
                    }
                }
                
                int regionStart = -1;
                byte regionOrf = 0;
                for (int pos = gene.getStart(); pos < gene.getEnd(); pos++) {
                    byte orf = buf[pos-gene.getStart()];
                    if (orf != regionOrf) {
                        if (regionOrf > 0) {
                            writer.write(gene.getRef());
                            writer.write(regionStart);
                            writer.write(pos);
                            writer.write(gene.getGeneName()+"/frame-"+orfBitmapToString(regionOrf));
                            writer.write(0);
                            writer.write(gene.getStrand().toString());
                            writer.eol();
                        }
                        
                        regionOrf = orf;
                        regionStart = pos;
                    }
                        
                }
                if (regionOrf > 0) {
                    writer.write(gene.getRef());
                    writer.write(regionStart);
                    writer.write(gene.getEnd());
                    writer.write(gene.getGeneName()+"/frame-"+orfBitmapToString(regionOrf));
                    writer.write(0);
                    writer.write(gene.getStrand().toString());
                    writer.eol();
                }
            }
        }
        
        writer.close();
    }

    private String orfBitmapToString(byte regionOrf) {
        if (regionOrf == 1) {
            return "1";
        }
        if (regionOrf == 2) {
            return "2";
        }
        if (regionOrf == 4) {
            return "3";
        }

        String s = "";
        if ((regionOrf & 0x1)>0) {
            s += "1";
        }
        if ((regionOrf & 0x2)>0) {
            if (!s.equals("")) {
                s += ",";
            }
            s += "2";
        }
        if ((regionOrf & 0x4)>0) {
            if (!s.equals("")) {
                s += ",";
            }
            s += "3";
        }
        return s;
    }
}
