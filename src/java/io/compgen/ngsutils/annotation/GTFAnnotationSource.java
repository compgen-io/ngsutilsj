package io.compgen.ngsutils.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.support.SeqUtils;

/**
 * Class to extract gene annotations stored in a GTF file.
 * 
 * @author mbreese
 * 
 */
public class GTFAnnotationSource extends AbstractAnnotationSource<GTFGene> {

    public class GTFExon implements Comparable<GTFExon> {
        final private GTFTranscript parent;
        final private int start;
        final private int end;
        final private String[] attributes; // just an array in { KEY1, VALUE1, KEY2, VALUE2 } order

        public GTFExon(GTFTranscript parent, int start, int end, String[] attributes) {
            this.parent = parent;
            this.start = start;
            this.end = end;
            this.attributes = attributes;
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

        public String getAttribute(String key) {
            for (int i=0; i<attributes.length-1; i++) {
                if (attributes[i].equals(key)) {
                    return attributes[i+1];
                }
            }
            return null;
        }
        
        @Override
        public int compareTo(GTFExon o) {
            if (start < o.start) {
                return -1;
            } else if (start > o.start) {
                return 1;
            } else if (end < o.end) { 
                return -1;
            } else if (end > o.end) { 
                return 1;
            }
            
            return 0;
        }

        /** 
         * Returns the sequence for this exon
         * 
         * This doesn't switch strands!!! It always returns the PLUS strand sequence
         * 
         * @param fasta - Reference genome FASTA file
         * @return
         * @throws IOException
         */
		public String getSequence(FastaReader fasta) throws IOException {
			return fasta.fetchSequence(parent.getParent().getRef(), start, end);
		}
    }

    public class GTFTranscript {
        final private GTFGene parent;
        final private String transcriptId;
//        private String[] attributes; // just an array in { KEY1, VALUE1, KEY2, VALUE2 } order

        private int start = -1;
        private int end = -1;

        private int cdsStart = -1;
        private int cdsEnd = -1;

        
        // start/stop codons can span multiple exons, so they need to be treated as potentially discontinuous...
        private int startCodonStart = -1;
        private int startCodonEnd = -1;
        
        private int stopCodonStart = -1;
        private int stopCodonEnd = -1;
        
        List<GTFExon> exons = new ArrayList<GTFExon>();
        List<GTFExon> cds = new ArrayList<GTFExon>();
        List<GTFExon> startCodons = new ArrayList<GTFExon>();
        List<GTFExon> stopCodons = new ArrayList<GTFExon>();


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
        	// note, this is the last base before the stop codon
            return cdsEnd;
        }

        public List<GTFExon> getExons() {
            return exons;
        }

        public GenomeSpan toRegion() {
            return new GenomeSpan(parent.getRef(), start, end, parent.getStrand());
        }

        public void addExon(int start, int end, String[] attributes) {
            if (this.start == -1 || this.start > start) {
                this.start = start;
            }
            if (this.end == -1 || this.end < end) {
                this.end = end;
            }
            exons.add(new GTFExon(this, start, end, attributes));
            Collections.sort(exons);
        }

        public void addCDS(int start, int end, String[] attributes) {
            if (cdsStart == -1 || cdsStart > start) {
                cdsStart = start;
            }
            if (cdsEnd == -1 || cdsEnd < end) {
                cdsEnd = end;
            }
            cds.add(new GTFExon(this, start, end, attributes));
            Collections.sort(cds);
        }

        public void addStopCodon(int start, int end, String[] attributes) {
            if (stopCodonStart == -1 || stopCodonStart > start) {
            	stopCodonStart = start;
            }
            if (stopCodonEnd == -1 || stopCodonEnd < end) {
            	stopCodonEnd = end;
            }
            stopCodons.add(new GTFExon(this, start, end, attributes));
            Collections.sort(stopCodons);
        }

        public void addStartCodon(int start, int end, String[] attributes) {
            if (startCodonStart == -1 || startCodonStart > start) {
            	startCodonStart = start;
            }
            if (startCodonEnd == -1 || startCodonEnd < end) {
            	startCodonEnd = end;
            }
            startCodons.add(new GTFExon(this, start, end, attributes));
            Collections.sort(startCodons);
        }

        public int getStartCodonStart() {
        	return startCodonStart;
        }
        public int getStartCodonEnd() {
        	return startCodonEnd;
        }

        public int getStopCodonStart() {
        	return stopCodonStart;
        }
        public int getStopCodonEnd() {
        	return stopCodonEnd;
        }

        public List<GTFExon> getStartCodons() {
        	return startCodons;
        }
        
        public List<GTFExon> getStopCodons() {
        	return stopCodons;
        }

		public String getSequence(FastaReader fasta) throws IOException {
			String ret = "";
			for (GTFExon exon: exons) {
				String seq = exon.getSequence(fasta);
				ret += seq;
			}
			
			if (parent.getStrand() == Strand.MINUS) {
				return SeqUtils.revcomp(ret);
			}
			return ret;
		}
        
//        public int getStartCodon() {
//            if (parent.getStrand().equals(Strand.PLUS)) {
//                if (cdsStart > 0) {
//                    return cdsStart;
//                }
//            } else {
//                if (cdsEnd > 0) {
//                    return cdsEnd - 3;
//                }
//            }
//            return -1;
//        }
//
//        public int getStopCodon() {
//            if (parent.getStrand().equals(Strand.PLUS)) {
//                if (cdsEnd > 0) {
//                    return cdsEnd;
//                }
//            } else {
//                if (cdsStart > 0) {
//                    return cdsStart - 3;
//                }
//            }
//            return -1;
//        }

    }

    public class GTFGene implements Annotation {
        final private String ref;
        final private String geneId;
        final private String geneName;
        final private String bioType;
        final private String status;

        private int start = -1;
        private int end = -1;
        private final Strand strand;

        private final Map<String, GTFTranscript> transcripts = new HashMap<String, GTFTranscript>();

//        public GTFGene(GenomeSpan coord, String geneId, String geneName, String ref, Strand strand) {
//            this.geneId = geneId;
//            this.geneName = geneName;
//            bioType = null;
//            this.strand = strand;
//            this.ref = ref;
//        }

        public GTFGene(String geneId, String geneName, String ref, int start, int end, Strand strand, String bioType, String status) {
            this.geneId = geneId;
            this.geneName = geneName;
            this.ref = ref;
            this.strand = strand;
            this.bioType = bioType;
            this.status = status;
            this.start = start;
            this.end = end;
        }

        public String toString() {
            return "geneId:" + geneId+ " geneName:" + geneName + " bioType:" + bioType + " "  + " status:" + status + " " + ref + strand + ":" + start + "-" + end;
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

        public String getStatus() {
            return status;
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

        public void addExon(String transcriptId, int start, int end, String[] attributes) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addExon(start, end, attributes);
            if (this.start == -1 || this.start > start) {
                this.start = start;
            }
            if (this.end == -1 || this.end < end) {
                this.end = end;
            }
        }

        public void addCDS(String transcriptId, int start, int end, String[] attributes) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addCDS(start, end, attributes);
        }

        public void addStopCodon(String transcriptId, int start, int end, String[] attributes) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addStopCodon(start, end, attributes);
        }

        public void addStartCodon(String transcriptId, int start, int end, String[] attributes) {
            if (!transcripts.containsKey(transcriptId)) {
                transcripts.put(transcriptId, new GTFTranscript(this, transcriptId));
            }
            transcripts.get(transcriptId).addStartCodon(start, end, attributes);
        }

        public List<GTFTranscript> getTranscripts(boolean codingOnly, boolean nonCodingOnly) {
            if (!codingOnly && !nonCodingOnly) {
                return new ArrayList<GTFTranscript>(transcripts.values());
            } else if (codingOnly){
                List<GTFTranscript> codingTranscripts = new ArrayList<GTFTranscript>();
                for (GTFTranscript t: transcripts.values()) {
                    if (t.hasCDS()) {
                        codingTranscripts.add(t);
                    }
                }
                return codingTranscripts;
            } else if (nonCodingOnly){
                List<GTFTranscript> codingTranscripts = new ArrayList<GTFTranscript>();
                for (GTFTranscript t: transcripts.values()) {
                    if (!t.hasCDS()) {
                        codingTranscripts.add(t);
                    }
                }
                return codingTranscripts;
            }
            return null;
        }

        public List<GTFTranscript> getTranscripts() {
            return getTranscripts(false, false);
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

        public List<GenomeSpan> getExonRegions(boolean codingOnly) {
            SortedSet<GenomeSpan> exons = new TreeSet<GenomeSpan>();
            for (GTFTranscript t:transcripts.values()){
                if (!codingOnly || t.hasCDS()) {
                    for (GTFExon ex: t.getExons()) {
                        exons.add(ex.toRegion());
                    }
                }
            }
            return new ArrayList<GenomeSpan>(exons);
        }
        public List<GenomeSpan> getExonRegions() {
            return getExonRegions(false);
        }

        
        @Override
        public String[] toStringArray() {
            if (bioType == null && status == null) {
                return new String[] { geneId, geneName };
            } else if (bioType == null) {
                return new String[] { geneId, geneName, status };
            } else if (status == null) {
                return new String[] { geneId, geneName, bioType };
            } else {
                return new String[] { geneId, geneName, bioType, status };
            }
        }

        public GenomeSpan getCoord() {
            return new GenomeSpan(ref, start, end, strand);
        }

    }

    final private boolean hasBioType;
    final private boolean hasStatus;

    public GTFAnnotationSource(String filename, List<String> requiredTags) throws NumberFormatException, IOException {
        final Map<String, GTFGene> cache = new HashMap<String, GTFGene>();
        
        boolean hasBioType = false;
        boolean hasStatus = false;
        
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
            String geneStr = "";
            String bioType = null;
            String status = null;

            final String[] attrs = StringUtils.quotedSplit(cols[8], ';');
            List<String> exonAttributes = new ArrayList<String>();
            Set<String> exonTags = new HashSet<String>();
            
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
                    case "gene":
                    	geneStr = v;
                        break;
                    case "transcript_id":
                        transcriptId = v;
                        break;
                    case "gene_type":
                    case "gene_biotype":
                        bioType = v;
                        break;
                    case "gene_status":
                        status = v;
                        break;
                            
                    default:
                        exonAttributes.add(k);
                        exonAttributes.add(v);
                        
                        if (k.equals("tag")) {
                        	exonTags.add(v);
                        }
                    }
                }
            }
            if ("".equals(geneName) && !"".equals(geneStr)) {
            	// refseq GTFs use "gene" as opposed to "gene_name"
            	geneName = geneStr;
            }

            if (requiredTags != null) {
            	boolean fail = false;
	            for (String tag: requiredTags) {
	            	if (!exonTags.contains(tag)) {
	            		fail = true;
	            		break;
	            	}
	            }
	            if (fail) {
	            	// skip this entry
	            	continue;
	            }
            }
            
            if (hasBioType == false && bioType!=null && !bioType.equals("")) {
                hasBioType = true;
            }

            if (hasStatus == false && status!=null && !status.equals("")) {
                hasStatus = true;
            }

            final GTFGene gene;
            if (cache.containsKey(geneId)) {
                gene = cache.get(geneId);
            } else {
//                System.err.println("Adding gene: "+geneId+" ("+geneName+", "+ chrom +")");
            	// send start/end here for gene, as there are genes w/o transcript annotations (refseq)
                gene = new GTFGene(geneId, geneName, chrom, start, end, strand, bioType, status);
                cache.put(geneId, gene);
            }

            switch (recordType) {
            case "exon":
                gene.addExon(transcriptId, start, end, exonAttributes.toArray(new String[exonAttributes.size()]));
                break;

            case "CDS":
                gene.addCDS(transcriptId, start, end, exonAttributes.toArray(new String[exonAttributes.size()]));
                break;
                
            case "stop_codon":
                gene.addStopCodon(transcriptId, start, end, exonAttributes.toArray(new String[exonAttributes.size()]));
                break;

            case "start_codon":
	            gene.addStartCodon(transcriptId, start, end, exonAttributes.toArray(new String[exonAttributes.size()]));
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
        this.hasStatus = hasStatus;
    }

    @Override
    public String[] getAnnotationNames() {
        if (hasBioType && hasStatus) {
            return new String[] { "gene_id", "gene_name", "start", "end", "strand", "biotype", "status" };
        } else if (hasBioType) {
            return new String[] { "gene_id", "gene_name", "start", "end", "strand", "biotype" };
        } else if (hasStatus) {
            return new String[] { "gene_id", "gene_name", "start", "end", "strand", "status" };
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
    public GenicRegion findGenicRegion(SAMRecord read, Orientation orient) {
        boolean isJunction = false;
        
        for (CigarElement op: read.getCigar().getCigarElements()) {
            if (op.getOperator().equals(CigarOperator.N)) {
                isJunction = true;
                break;
            }
        }
        
        // assume FR (we just want to get the first strand in F direction)
        GenomeSpan readpos = GenomeSpan.getReadStartPos(read, orient); 
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

            if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                isGeneRev = true;
            }

            for (GTFTranscript txpt: gene.getTranscripts()) {
                if (txpt.hasCDS()) {
                    for (GTFExon cds: txpt.cds) {
                        if (cds.toRegion().contains(unstrandedPos)) {
                            isExon = true;
                            isCoding = true;
                            if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                                isCodingRev = true;
                            }
                        }
                    }
                    if (!isCoding) {
                        for (GTFExon exon: txpt.exons) {
                            if (exon.toRegion().contains(unstrandedPos)) {
                                isExon = true;
                                if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                                    isExonRev = true;
                                }
                            }
                        } 
                    }
                    if (isExon) {
                        if (gene.getStrand() == Strand.PLUS) {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr5 = true;
                                if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                                    isUtr5Rev = true;
                                }
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr3 = true;
                                if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                                    isUtr3Rev = true;
                                }
                            }
                        } else {
                            if (unstrandedPos.start < txpt.getCdsStart()) {
                                isUtr3 = true;
                                if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
                                    isUtr3Rev = true;
                                }
                            } else if (unstrandedPos.start > txpt.getCdsEnd()) {
                                isUtr5 = true;
                                if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
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
                            if (origStrand != Strand.NONE && gene.getStrand() != origStrand) {
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
