package org.ngsutils.annotation;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.bam.Orientation;
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
        
        public GenomeSpan toRegion() {
            return new GenomeSpan(parent.getParent().getRef(), start, end, parent.getParent().getStrand());
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

        public boolean hasCDS() {
            return (cdsStart > -1 && cdsEnd > -1);
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

        public GenomeSpan toRegion() {
            return new GenomeSpan(parent.getRef(), start, end, parent.getStrand());
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

        public GTFGene(GenomeSpan coord, String geneId, String geneName, String ref, Strand strand) {
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

        public List<GTFExon> getExons() {
            SortedMap<GenomeSpan, GTFExon> exons = new TreeMap<GenomeSpan, GTFExon>();
            for (GTFTranscript t:transcripts.values()){
                for (GTFExon ex: t.getExons()) {
                    exons.put(ex.toRegion(), ex);
                }
            }
            return new ArrayList<GTFExon>(exons.values());
        }

        public List<GenomeSpan> getExonRegions() {
            SortedSet<GenomeSpan> exons = new TreeSet<GenomeSpan>();
            for (GTFTranscript t:transcripts.values()){
                for (GTFExon ex: t.getExons()) {
                    exons.add(ex.toRegion());
                }
            }
            return new ArrayList<GenomeSpan>(exons);
        }

        
        @Override
        public String[] toStringArray() {
            if (bioType == null) {
                return new String[] { geneId, geneName };
            } else {
                return new String[] { geneId, geneName, bioType };
            }
        }

        public GenomeSpan getCoord() {
            return new GenomeSpan(ref, start, end, strand);
        }

    }

    final private boolean hasBioType;

    public GTFAnnotationSource(String filename) throws NumberFormatException, IOException {
        final Map<String, GTFGene> cache = new HashMap<String, GTFGene>();
        boolean hasBioType = false;
        String lastChrom = null;
        for (final String line : new StringLineReader(filename)) {
            if (line.charAt(0) == '#') {
                continue;
            }
            final String[] cols = StringUtils.strip(line).split("\t", -1);

            final String chrom = cols[0];
            final String recordType = cols[2];
            final int start = Integer.parseInt(cols[3]) - 1; // file is 1-based
            final int end = Integer.parseInt(cols[4]);
            final Strand strand = Strand.parse(cols[6]);

            /*
             *  If we don't clear the cache, genes that appear on multiple
             *  chromosomes (miRNAs?) will get their start/end coordinates all
             *  messed up.
             */
            if (!chrom.equals(lastChrom) ) {
                for (final String geneId : cache.keySet()) {
                    final GTFGene gene = cache.get(geneId);
                    final GenomeSpan coord = new GenomeSpan(gene.getRef(),
                            gene.getStart(), gene.getEnd(), gene.getStrand());
                    addAnnotation(coord, gene);
                }
                cache.clear();
                lastChrom = chrom;
            }
            
            String geneId = "";
            String transcriptId = "";
            String geneName = "";
            String bioType = null;

            final String[] attrs = StringUtils.quotedSplit(cols[8], ';');
            for (final String attr : attrs) {
                if (StringUtils.strip(attr).length() > 0) {
                    final String[] kv = StringUtils.strip(attr).split(" ", 2);
                    final String k = kv[0];
                    final String v = StringUtils.strip(kv[1], "\"");
//                    System.err.println("Attribute: "+ attr+" k="+k+", v="+v);
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
            }

            if (hasBioType == false && bioType!=null && !bioType.equals("")) {
                hasBioType = true;
            }

            final GTFGene gene;
            if (cache.containsKey(geneId)) {
                gene = cache.get(geneId);
            } else {
//                System.err.println("Adding gene: "+geneId+" ("+geneName+", "+ chrom +")");
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
            final GenomeSpan coord = new GenomeSpan(gene.getRef(),
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

    /**
     * Find the gene associate with a particular splice-junction.
     * 
     * Junctions are specified like this:
     *      ref:start-end
     *      
     * Where start and end are 0-based coordinates of the start and end of the intron. 
     * (Or the end of the first exon and the start of the second one since we are zero-based)
     *
     * @param junction
     * @return
     */
    public List<GTFGene> findJunction(String junction) {
        String ref = junction.substring(0,junction.indexOf(':'));
        String startend = junction.substring(junction.indexOf(':')+1);

        int start;
        int end;
        
        if (startend.indexOf('-') > -1) {
            start = Integer.parseInt(startend.substring(0,startend.indexOf('-')));
            end = Integer.parseInt(startend.substring(startend.indexOf('-')+1));
        } else {
            start = Integer.parseInt(startend);
            end = start;
        }
        
        List<GTFGene>  retval = new ArrayList<GTFGene>();
        
        for (GTFGene gene: findAnnotation(new GenomeSpan(ref, start))) {
            boolean matchStart = false;
            boolean matchEnd = false;
            for (GTFExon exon: gene.getExons()) {
                if (exon.end == start) {
                    matchStart = true;
                }
                
                if (exon.start == end) {
                    matchEnd = true;
                }
            }
            
            if (matchStart && matchEnd) {
                retval.add(gene);
            } else if (start == end && (matchStart || matchEnd)) {
                retval.add(gene);
            }
        }
        
        return retval;
    }

    /**
     * Find the Genic region for this read
     * This could be either sense or anti-sense
     * @param read
     * @return
     */
    public GenicRegion findGenicRegion(SAMRecord read) {
        boolean isJunction = false;
        
        for (CigarElement op: read.getCigar().getCigarElements()) {
            if (op.getOperator().equals(CigarOperator.N)) {
                isJunction = true;
                break;
            }
        }
        
        GenomeSpan readpos = GenomeSpan.getReadStartPos(read, Orientation.UNSTRANDED);        
        GenicRegion geneReg = findGenicRegionForPos(readpos);
        
        if (isJunction && geneReg.isGene) {
            if (geneReg.isCoding) {
                if (geneReg.isSense) {
                    return GenicRegion.JUNCTION;
                }
                return GenicRegion.JUNCTION_ANTI;
            } else {
                if (geneReg.isSense) {
                    return GenicRegion.NC_JUNCTION;
                }
                return GenicRegion.NC_JUNCTION_ANTI;
            }
        }
        
        return geneReg; 

    }

    public GenicRegion findGenicRegionForRegion(GenomeSpan reg) {
        GenicRegion genStart = findGenicRegionForPos(reg.getStartPos());
        GenicRegion genEnd = findGenicRegionForPos(reg.getEndPos());

        // If we agree, just return one.
        if (genStart == genEnd) {
            return genStart;
        }
        
        // If one end is in a gene and the other isn't, use the gene annotation
        if (genStart.isGene && !genEnd.isGene) {
            return genStart;
        }
        if (!genStart.isGene && genEnd.isGene) {
            return genEnd;
        }
        
        // If one end is in an exon and the other isn't, we must be crossing a junction. (this will miss junctions we completely span...)
        if (genStart.isExon && !genEnd.isExon) {
            if (genStart.isCoding) {
                if (genStart.isSense) {
                    return GenicRegion.JUNCTION;
                }
                return GenicRegion.JUNCTION_ANTI;
            } else {
                if (genStart.isSense) {
                    return GenicRegion.NC_JUNCTION;
                }
                return GenicRegion.NC_JUNCTION_ANTI;
            }
        }

        if (!genStart.isExon && genEnd.isExon) {
            if (genEnd.isCoding) {
                if (genEnd.isSense) {
                    return GenicRegion.JUNCTION;
                }
                return GenicRegion.JUNCTION_ANTI;
            } else {
                if (genEnd.isSense) {
                    return GenicRegion.NC_JUNCTION;
                }
                return GenicRegion.NC_JUNCTION_ANTI;
            }
        }

        // If one end is coding, and the other isn't, call it coding
        if (genStart.isCoding && !genEnd.isCoding) {
            return genStart;
        }
        if (!genStart.isCoding && genEnd.isCoding) {
            return genEnd;
        }

        // When in doubt, just return based on priority (lower is better)
        if (genStart.compareTo(genEnd) < 0) {
            return genStart;
        }
        return genEnd;
    
    }

    
    public GenicRegion findGenicRegionForPos(GenomeSpan pos) {
        return findGenicRegionForPos(pos, null);
    }
    
    public GenicRegion findGenicRegionForPos(GenomeSpan pos, String geneId) {
        if (pos.ref.equals("chrM") || pos.ref.equals("M")) {
            return GenicRegion.MITOCHONDRIAL;
        }

        boolean isGene = false;
        boolean isExon = false;
        boolean isCoding = false;
        boolean isUtr3 = false;
        boolean isUtr5 = false;

        boolean isCodingIntron = false;
        boolean isUtr3Intron = false;
        boolean isUtr5Intron = false;

        boolean isGeneRev = false;
        boolean isExonRev = false;
        boolean isCodingRev = false;
        boolean isUtr3Rev = false;
        boolean isUtr5Rev = false;

        Strand origStrand = pos.strand;
        GenomeSpan unstrandedPos = pos.clone(Strand.NONE);
        
        for(GTFGene gene: findAnnotation(unstrandedPos)) {
            if (geneId != null && !gene.getGeneId().equals(geneId)) {
                continue;
            }
            isGene = true;

            if (gene.getStrand() != origStrand) {
                isGeneRev = true;
            }

            for (GTFTranscript txpt: gene.getTranscripts()) {
                if (txpt.hasCDS()) {
                    for (GTFExon cds: txpt.cds) {
                        if (cds.toRegion().contains(unstrandedPos)) {
                            isExon = true;
                            isCoding = true;
                            if (gene.getStrand() != origStrand) {
                                isCodingRev = true;
                            }
                        }
                    }
                    if (!isCoding) {
                        for (GTFExon exon: txpt.exons) {
                            if (exon.toRegion().contains(unstrandedPos)) {
                                isExon = true;
                                if (gene.getStrand() != origStrand) {
                                    isExonRev = true;
                                }
                            }
                        } 
                    }
                    if (isExon) {
                        if (gene.getStrand() == Strand.PLUS) {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr5 = true;
                                if (gene.getStrand() != origStrand) {
                                    isUtr5Rev = true;
                                }
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr3 = true;
                                if (gene.getStrand() != origStrand) {
                                    isUtr3Rev = true;
                                }
                            }
                        } else {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr3 = true;
                                if (gene.getStrand() != origStrand) {
                                    isUtr3Rev = true;
                                }
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr5 = true;
                                if (gene.getStrand() != origStrand) {
                                    isUtr5Rev = true;
                                }
                            }
                        }
                    } else {
                        if (gene.getStrand() == Strand.PLUS) {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr5Intron = true; 
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr3Intron = true; 
                            } else {
                                isCodingIntron = true;
                            }
                        } else {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr3Intron = true; 
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr5Intron = true; 
                            } else {
                                isCodingIntron = true;
                            }
                        }
                    }             
                } else {
                    for (GTFExon exon: txpt.exons) {
                        if (exon.toRegion().contains(unstrandedPos)) {
                            isExon = true;
                            if (gene.getStrand() != origStrand) {
                                isExonRev = true;
                            }
                        }
                    }
                }
            }
        }
        
        if (isCoding) {
            if (isCodingRev) {
                return GenicRegion.CODING_ANTI;
            } else {
                return GenicRegion.CODING;
            }
        }
        
        if (isUtr5) {
            if (isUtr5Rev) {
                return GenicRegion.UTR5_ANTI;
            } else {
                return GenicRegion.UTR5;
            }
        }

        if (isUtr3) {
            if (isUtr3Rev) {
                return GenicRegion.UTR3_ANTI;
            } else {
                return GenicRegion.UTR3;
            }
        }
        
        if (isExon) {
            if (isExonRev) {
                return GenicRegion.NC_EXON_ANTI;
            } else {
                return GenicRegion.NC_EXON;
            }
        }

        if (isGene) {
            if (isGeneRev) {
                if (isCodingIntron) {
                    return GenicRegion.CODING_INTRON_ANTI;
                } else if (isUtr5Intron) {
                    return GenicRegion.UTR5_INTRON_ANTI;
                } else if (isUtr3Intron) {
                    return GenicRegion.UTR3_INTRON_ANTI;
                } else {
                    return GenicRegion.NC_INTRON_ANTI;
                }
            } else {
                if (isCodingIntron) {
                    return GenicRegion.CODING_INTRON;
                } else if (isUtr5Intron) {
                    return GenicRegion.UTR5_INTRON;
                } else if (isUtr3Intron) {
                    return GenicRegion.UTR3_INTRON;
                } else {
                    return GenicRegion.NC_INTRON;
                }
            }
        }
        
        return GenicRegion.INTERGENIC;
    }    
}
