package io.compgen.ngsutils.bam;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMRecord;
import io.compgen.common.RadixSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FindDuplicateReads {
    private SAMFileWriter writer = null;
    private SAMFileWriter failedWriter = null;
    private boolean removeDuplicates = false;
    
    // Store a copy of all "duplicate" reads that have inter-chromosomal mappings...
    // These will be harder to call, so we'll keep track of what we've already done

    private Set<String> splitDuplicates = new RadixSet(); // more memory efficient than HashSet
    private Set<String> pairedDuplicates = new RadixSet();
    
    private long unmapped = 0;
    private long duplicateSites = 0;
    private long duplicateReads = 0;
    
    private Map<Integer, List<SAMRecord>> buffer = new TreeMap<Integer, List<SAMRecord>>();
    private int curRefIdx = -1;
    private int curRefPos = -1;
    
    public FindDuplicateReads(SAMFileWriter writer, boolean removeDuplicates, SAMFileWriter failedWriter) {
        this.writer = writer;
        this.removeDuplicates = removeDuplicates;
        this.failedWriter = failedWriter;
    }

    public FindDuplicateReads(SAMFileWriter writer, boolean removeDuplicates) {
        this(writer, removeDuplicates, null);
    }

    public void addRead(SAMRecord read) {
        if (read.getReferenceIndex() != curRefIdx || read.getAlignmentStart() != curRefPos) {
            flushBuffer();
            curRefIdx = read.getReferenceIndex();
            curRefPos = read.getAlignmentStart();
        }

        // punt on unmapped reads
        if (read.getReadUnmappedFlag() || read.getMateUnmappedFlag()) {
            unmapped++;
            writer.addAlignment(read);
            return;
        }
        
        if (read.getReferenceIndex() != read.getMateReferenceIndex()) {
            // This is a split read - have we already seen it?
            if (splitDuplicates.contains(read.getReadName())) {
                handleDuplicate(read);
                return;
            }
        } else if (read.getInferredInsertSize() < 0) {
            // this is not a split read, but is the 2nd read of a pair (tlen is negative)
            // so, if we've already seen the first read, was it a duplicate?
            if (pairedDuplicates.contains(read.getReadName())) {
                pairedDuplicates.remove(read.getReadName());
                handleDuplicate(read);
                return;
            } else {
                // wasn't a duplicate last time around, skip processing this time around.
                writer.addAlignment(read);
                return;
            }
        }
        
        // mapped pair, intra-chromosomal, first read of the pair (in the file).

        if (!buffer.containsKey(read.getInferredInsertSize())) {
            buffer.put(read.getInferredInsertSize(), new ArrayList<SAMRecord>());
        }

        buffer.get(read.getInferredInsertSize()).add(read);
    }
    
    private void handleDuplicate(SAMRecord read) {
        duplicateReads++;
        if (failedWriter != null) {
            read.setDuplicateReadFlag(true);
            failedWriter.addAlignment(read);
        } else if (!removeDuplicates) {
            read.setDuplicateReadFlag(true);
            writer.addAlignment(read);
        }
        // else - silently drop
    }

    public void close() {
        flushBuffer();
    }
    
    public void clear() {
        buffer.clear();
        splitDuplicates.clear();
        pairedDuplicates.clear();
        curRefIdx = -1;
        curRefPos = -1;
    }
    
    private void flushBuffer() {
        for (Integer tlen: buffer.keySet()) {
            if (buffer.get(tlen).size() == 1) {
                // singleton
                writer.addAlignment(buffer.get(tlen).get(0));
            } else if (tlen == 0) {
                // potential duplicates spanning chromosomes... 
                // tlen isn't accurate here, so we will only look at the start pos for the paired alignment.
                
                // TODO: Explicitly search for mate records to get exact endpoint???
                
                
                // key: <chromIdx, startPos> => list(reads)
                Map<Integer, Map<Integer,List<SAMRecord>>> pairs = new TreeMap<Integer, Map<Integer, List<SAMRecord>>> ();
                
                for (int i=0; i<buffer.get(tlen).size(); i++) {
                    SAMRecord read = buffer.get(tlen).get(i);
                    
                    if (!pairs.containsKey(read.getMateReferenceIndex())) {
                        pairs.put(read.getMateReferenceIndex(), new TreeMap<Integer, List<SAMRecord>>());
                    }
                    
                    if (!pairs.get(read.getMateReferenceIndex()).containsKey(read.getMateAlignmentStart())) {
                        pairs.get(read.getMateReferenceIndex()).put(read.getMateAlignmentStart(), new ArrayList<SAMRecord>());
                    }
                    pairs.get(read.getMateReferenceIndex()).get(read.getMateAlignmentStart()).add(read);
                }
                
                for (Integer refIdx: pairs.keySet()) {
                    for (Integer pos: pairs.get(refIdx).keySet()) {
                        if (pairs.get(refIdx).get(pos).size() == 1) {
                            writer.addAlignment(pairs.get(refIdx).get(pos).get(0));
                        } else {
                            duplicateSites++;
                            findDuplicates(pairs.get(refIdx).get(pos));
                        }
                        
                    }
                }
            } else {
                duplicateSites++;
                findDuplicates(buffer.get(tlen));
            }
        }
        buffer.clear();        
    }
    
    private void findDuplicates(List<SAMRecord> list) {
        // duplicate start/tlen, find the best MAPQ, and keep that one.
        int bestMAPQ = -1;
        int bestAS = -1;
        int bestIdx = -1;
        
        for (int i=0; i<list.size(); i++) {
            SAMRecord read = list.get(i);
            Integer asTag = read.getIntegerAttribute("AS");

            if (read.getMappingQuality() > bestMAPQ) {
                bestMAPQ = read.getMappingQuality();
                bestIdx = i;
                
                if (asTag != null) {
                    bestAS = asTag;
                }
            } else if (read.getMappingQuality() == bestMAPQ) {
                if (asTag != null && asTag > bestAS) {
                    bestAS = asTag;
                    bestIdx = i;
                }
            }
        }
       
        for (int i=0; i<list.size(); i++) {
            SAMRecord read = list.get(i);
            if (i == bestIdx) {
                writer.addAlignment(read);
            } else {
                if (read.getReferenceIndex()!=read.getMateReferenceIndex()) {
                    splitDuplicates.add(read.getReadName());
                } else {
                    pairedDuplicates.add(read.getReadName());
                }
                handleDuplicate(read);
            }
        }
    }
    
    public long getDuplicateSites() {
        return duplicateSites;
    }

    public long getDuplicateReads() {
        return duplicateReads;
    }
    
    public long getUnmappedReads() {
        return unmapped;
    }
    
}
