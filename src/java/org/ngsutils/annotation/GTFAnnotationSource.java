package org.ngsutils.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.bam.Strand;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;

/**
 * Class to extract gene annotations stored in a GTF file.
 * 
 * @author mbreese
 * 
 */
public class GTFAnnotationSource extends AbstractAnnotationSource<GTFGene> {

    public class GTFExon {
        final private GTFTranscript parent;
        final private int start;
        final private int end;

        public GTFExon(GTFTranscript parent, int start, int end) {
            this.parent = parent;
            this.start = start;
            this.end = end;
        }

        public GTFTranscript getParent() {
            return parent;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
        
        public GenomeRegion toRegion() {
            return new GenomeRegion(parent.getParent().getRef(), start, end, parent.getParent().getStrand());
        }
    }

    public class GTFTranscript {
        final private GTFGene parent;
        final private String transcriptId;

        private int start = -1;
        private int end = -1;

        private int cdsStart = -1;
        private int cdsEnd = -1;

        List<GTFExon> exons = new ArrayList<GTFExon>();
        List<GTFExon> cds = new ArrayList<GTFExon>();

        public GTFTranscript(GTFGene parent, String transcriptId) {
            this.parent = parent;
            this.transcriptId = transcriptId;
        }

        public GTFGene getParent() {
            return parent;
        }

        public String getTranscriptId() {
            return transcriptId;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public int getCdsStart() {
            return cdsStart;
        }

        public int getCdsEnd() {
            return cdsEnd;
        }

        public List<GTFExon> getExons() {
            return exons;
        }

        public GenomeRegion toRegion() {
            return new GenomeRegion(parent.getRef(), start, end, parent.getStrand());
        }

        public void addExon(int start, int end) {
            if (this.start == -1 || this.start > start) {
                this.start = start;
            }
            if (this.end == -1 || this.end < end) {
                this.end = end;
            }
            exons.add(new GTFExon(this, start, end));
        }

        public void addCDS(int start, int end) {
            if (cdsStart == -1 || cdsStart > start) {
                cdsStart = start;
            }
            if (cdsEnd == -1 || cdsEnd < end) {
                cdsEnd = end;
            }
            cds.add(new GTFExon(this, start, end));
        }

        public int getStartCodon() {
            if (parent.getStrand().equals(Strand.PLUS)) {
                if (cdsStart > 0) {
                    return cdsStart;
                }
            } else {
                if (cdsEnd > 0) {
                    return cdsEnd - 3;
                }
            }
            return -1;
        }

        public int getStopCodon() {
            if (parent.getStrand().equals(Strand.PLUS)) {
                if (cdsEnd > 0) {
                    return cdsEnd;
                }
            } else {
                if (cdsStart > 0) {
                    return cdsStart - 3;
                }
            }
            return -1;
        }

    }

    public class GTFGene implements Annotation {
        final private String ref;
        final private String geneId;
        final private String geneName;
        final private String bioType;

        private int start = -1;
        private int end = -1;
        private final Strand strand;

        private final Map<String, GTFTranscript> transcripts = new HashMap<String, GTFTranscript>();

        public GTFGene(String geneId, String geneName, String ref, Strand strand) {
            this.geneId = geneId;
            this.geneName = geneName;
            bioType = null;
            this.strand = strand;
            this.ref = ref;
        }

        public GTFGene(String geneId, String geneName, String ref, Strand strand, String bioType) {
            this.geneId = geneId;
            this.geneName = geneName;
            this.bioType = bioType;
            this.strand = strand;
            this.ref = ref;
        }

        public String toString() {
            return "geneId:" + geneId+ " geneName:" + geneName + " bioType:" + bioType + " " + ref + strand + ":" + start + "-" + end;
        }
        
        public String getRef() {
            return ref;
        }

        public String getGeneId() {
            return geneId;
        }

        public String getGeneName() {
            return geneName;
        }

        public String getBioType() {
            return bioType;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public Strand getStrand() {
            return strand;
        }

        public void addExon(String transcriptId, int start, int end) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addExon(start, end);
            if (this.start == -1 || this.start > start) {
                this.start = start;
            }
            if (this.end == -1 || this.end < end) {
                this.end = end;
            }
        }

        public void addCDS(String transcriptId, int start, int end) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addCDS(start, end);
        }

        public List<GTFTranscript> getTranscripts() {
            return new ArrayList<GTFTranscript>(transcripts.values());
        }

        public List<GenomeRegion> getExons() {
            SortedSet<GenomeRegion> exons = new TreeSet<GenomeRegion>();
//            System.err.println("Getting exons for gene: "+geneName);
//            System.err.println("  txpts: "+transcripts.size() + " " + StringUtils.join(",",transcripts.keySet()));
            for (GTFTranscript t:transcripts.values()){
//                System.err.println("  txpt: "+t.transcriptId);
                for (GTFExon ex: t.getExons()) {
//                    System.err.println("    exon: "+ex.toString());
                    exons.add(ex.toRegion());
                }
            }
            return new ArrayList<GenomeRegion>(exons);
        }
        
        @Override
        public String[] toStringArray() {
            if (bioType == null) {
                return new String[] { geneId, geneName };
            } else {
                return new String[] { geneId, geneName, bioType };
            }
        }

        public GenomeRegion toRegion() {
            return new GenomeRegion(ref, start, end, strand);
        }
    }

    final private boolean hasBioType;

    public GTFAnnotationSource(String filename) throws NumberFormatException, IOException {
        final Map<String, GTFGene> cache = new HashMap<String, GTFGene>();
        boolean hasBioType = false;
        for (final String line : new StringLineReader(filename)) {
            final String[] cols = StringUtils.strip(line).split("\t", -1);

            final String chrom = cols[0];
            final String recordType = cols[2];
            final int start = Integer.parseInt(cols[3]) - 1; // file is 1-based
            final int end = Integer.parseInt(cols[4]);
            final Strand strand = Strand.parse(cols[6]);

            String geneId = "";
            String transcriptId = "";
            String geneName = "";
            String bioType = null;

            final String[] attrs = cols[8].split(";");
            for (final String attr : attrs) {
                final String[] kv = StringUtils.strip(attr).split(" ", 2);
                final String k = kv[0];
                final String v = StringUtils.strip(kv[1], "\"");
                switch (k) {
                case "gene_id":
                    geneId = v;
                    break;
                case "gene_name":
                    geneName = v;
                    break;
                case "transcript_id":
                    transcriptId = v;
                    break;
                case "gene_biotype":
                    bioType = v;
                    break;
                }
            }

            if (hasBioType == false && !bioType.equals("")) {
                hasBioType = true;
            }

            final GTFGene gene;
            if (cache.containsKey(geneId)) {
                gene = cache.get(geneId);
            } else {
                gene = new GTFGene(geneId, geneName, chrom, strand, bioType);
                cache.put(geneId, gene);
            }

            switch (recordType) {
            case "exon":
                gene.addExon(transcriptId, start, end);
                break;

            case "CDS":
                gene.addCDS(transcriptId, start, end);
                break;
            }
        }

        for (final String geneId : cache.keySet()) {
            final GTFGene gene = cache.get(geneId);
            final GenomeRegion coord = new GenomeRegion(gene.getRef(),
                    gene.getStart(), gene.getEnd(), gene.getStrand());
            addAnnotation(coord, gene);
        }

        this.hasBioType = hasBioType;
    }

    @Override
    public String[] getAnnotationNames() {
        if (hasBioType) {
            return new String[] { "gene_id", "gene_name", "start", "end", "strand", "biotype" };
        } else {
            return new String[] { "gene_id", "gene_name", "start", "end", "strand" };
        }
    }

}
