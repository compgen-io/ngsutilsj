package io.compgen.ngsutils.cli.bam.count;

import io.compgen.common.IterUtils;
import io.compgen.common.IterUtils.MapFunc;
import io.compgen.common.StringUtils;
import io.compgen.common.TTY;
import io.compgen.ngsutils.annotation.GTFAnnotationSource;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFExon;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFTranscript;
import io.compgen.ngsutils.annotation.GenomeAnnotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GTFSpans  implements SpanSource {
    private final GTFAnnotationSource gtf;
    private long pos = 0;
    private long size = 0;
    
    public GTFSpans(String filename) throws IOException {
        if (TTY.isattyStdErr()) {
            System.err.print("Loading GTF annotation: "+filename+"... ");
        }
        gtf = new GTFAnnotationSource(filename);
        if (TTY.isattyStdErr()) {
            System.err.println("done");
        }
    }
    
    public long position() {
        return pos;
    }
    
    public long size() {
        return size;
    }
    

    @Override
    public String[] getHeader() {
        if (gtf.provides("biotype") && gtf.provides("status")) {
            return new String[]{ "gene_id", "gene_name", "gene_type", "gene_status", "transcript_ids", "chrom", "start", "end", "strand"};
        }
        if (gtf.provides("biotype")) {
            return new String[]{ "gene_id", "gene_name", "gene_type", "transcript_ids", "chrom", "start", "end", "strand"};
        }
        if (gtf.provides("status")) {
            return new String[]{ "gene_id", "gene_name", "gene_status", "transcript_ids", "chrom", "start", "end", "strand"};
        }
        return new String[]{ "gene_id", "gene_name", "transcript_ids", "chrom", "start", "end", "strand"};

    }

    @Override
    public Iterator<Span> iterator() {
        pos = 0;
        size = gtf.size();
        return new Iterator<Span>() {
            Iterator<GenomeAnnotation<GTFGene>> it = gtf.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Span next() {
                pos++;
                GenomeAnnotation<GTFGene> ann = it.next();
                GTFGene gene = ann.getValue();
                String txptids = StringUtils.join(",", gene.getTranscripts(), new MapFunc<GTFTranscript, String>(){
                    @Override
                    public String map(GTFTranscript obj) {
                        return obj.getTranscriptId();
                    }});
                
                String[] fields;
                if (gtf.provides("biotype") && gtf.provides("status")) {
                    fields = new String[] { 
                            gene.getGeneId(), 
                            gene.getGeneName(),
                            gene.getBioType(),
                            gene.getStatus(),
                            txptids, 
                            gene.getRef(), 
                            ""+gene.getStart(), 
                            ""+gene.getEnd(), 
                            gene.getStrand().toString() };
                } else if (gtf.provides("biotype")) {
                    fields = new String[] { 
                            gene.getGeneId(), 
                            gene.getGeneName(),
                            gene.getBioType(),
                            txptids, 
                            gene.getRef(), 
                            ""+gene.getStart(), 
                            ""+gene.getEnd(), 
                            gene.getStrand().toString() };
                } else if (gtf.provides("status")) {
                    fields = new String[] { 
                            gene.getGeneId(), 
                            gene.getGeneName(),
                            gene.getStatus(),
                            txptids, 
                            gene.getRef(), 
                            ""+gene.getStart(), 
                            ""+gene.getEnd(), 
                            gene.getStrand().toString() };
                } else {
                    fields = new String[] { 
                            gene.getGeneId(), 
                            gene.getGeneName(),
                            txptids, 
                            gene.getRef(), 
                            ""+gene.getStart(), 
                            ""+gene.getEnd(), 
                            gene.getStrand().toString() };
                }

                List<Integer> starts = new ArrayList<Integer>(); 
                List<Integer> ends = new ArrayList<Integer>(); 
                
                for (GTFExon exon: gene.getExons()) {
                    starts.add(exon.getStart());
                    ends.add(exon.getEnd());
                }
                
                return new Span(gene.getRef(), IterUtils.intListToArray(starts), IterUtils.intListToArray(ends), gene.getStrand(), fields);
            }

            @Override
            public void remove() {
                it.remove();
            }};
    }
}
