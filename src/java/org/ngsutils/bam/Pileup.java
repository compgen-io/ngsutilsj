package org.ngsutils.bam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecordIterator;

import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.fasta.FASTAFile;

public class Pileup {
    public class RefPos implements Comparable<RefPos> {
        public final int refIndex;
        public final int pos; // 1-based
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + pos;
            result = prime * result + refIndex;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RefPos other = (RefPos) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (pos != other.pos) {
                return false;
            }
            if (refIndex != other.refIndex) {
                return false;
            }
            return true;
        }

        public RefPos(int refIndex, int pos) {
            this.refIndex = refIndex;
            this.pos = pos;
        }

        private Pileup getOuterType() {
            return Pileup.this;
        }

        @Override
        public int compareTo(RefPos o) {
            if (o == this) {
                return 0;
            }
            
            if (o.refIndex == refIndex) {
                return Integer.compare(pos, o.pos);
            }
            
            return Integer.compare(refIndex, o.refIndex);
        }
    }
    
    public class PileupRead {
        public final SAMRecord read;
        public final int readPos; // 0-based
        public final int length;
        public final CigarOperator cigarOp;
        private boolean isStart;
        private boolean isEnd = false;
        
        
        public PileupRead(SAMRecord read, int readPos, CigarOperator cigarOp, int length, boolean isStart) {
            this.read = read;
            this.readPos = readPos;
            this.cigarOp = cigarOp;
            this.length = length;
            this.isStart = isStart;
        }

        public boolean isStart() {
            return this.isStart;
        }
        
        public boolean isEnd() {
            return this.isEnd;
        }
        
        public void setEnd() {
            this.isEnd = true;
        }
        
        public Strand getStrand() {
            if (read.getReadNegativeStrandFlag()) {
                return Strand.MINUS;
            }
            return Strand.PLUS;
        }
        
        public String getBaseCall() {
            if (cigarOp == CigarOperator.M) {
                return read.getReadString().substring(readPos, readPos+1);
            } else if (cigarOp == CigarOperator.D) {
                return "-";
            } else if (cigarOp == CigarOperator.I) {
                return read.getReadString().substring(readPos, readPos+length);
            }
            return "?";
        }
        public String getStrandedBaseCall(String refBase) {
            String call = getBaseCall();
            if (call.equals(refBase)) {
                if (read.getReadNegativeStrandFlag()) {
                    return ",";
                }
                return ".";
            }
            if (getStrand() == Strand.PLUS) {
                return call.toUpperCase();
            } else {
                return call.toLowerCase();
            }
        }

        public char getBaseQual() {
            if (cigarOp != CigarOperator.M) {
                return '!'; // Phred 33
            }
            return (char) (read.getBaseQualities()[readPos] + 33);
        }
    }

    public class PileupPos {
        public final int refIndex;
        public final int pos; // 1-based
        public final String refBase;
        
        private int coverage;
        private List<PileupRead> reads = new ArrayList<PileupRead>();
        private PileupRead last = null;

        public PileupPos(int refIndex, int pos, String refBase) {
            this.refIndex = refIndex;
            this.pos = pos;
            this.refBase = refBase.toUpperCase();
        }
        
        public PileupPos(int refIndex, int pos) {
            this.refIndex = refIndex;
            this.pos = pos;
            this.refBase = "N";
        }
        
        private void addRead(SAMRecord read, int readPos, CigarOperator cigarOp, int cigarLen, boolean isStart) {
            if (cigarOp == CigarOperator.M) {
                last = new PileupRead(read, readPos, cigarOp, 1, isStart);
                reads.add(last);
                coverage += 1;
            } else if (cigarOp == CigarOperator.I) {
                reads.add(new PileupRead(read, readPos, cigarOp, cigarLen, isStart));
            } else if (cigarOp == CigarOperator.D) {
                reads.add(new PileupRead(read, readPos, cigarOp, cigarLen, isStart));
            }
        }
        
        public List<PileupRead> getReads() {
            return Collections.unmodifiableList(reads);
        }
        
        public int getCoverage() {
            return coverage;
        }
        
        public PileupRead getLast() {
            return last;
        }
    }

    private SAMFileReader reader;
    private SAMRecordIterator samIterator = null;
    private FASTAFile fastaRef = null;
    
    private int minMappingQual = 0;
    private int minBaseQual = 0;
    
    private int flagFilter = 1796; // by default the read must be mapped.
    private int flagRequired = 0;
    
    public Pileup(String samFilename) {
        this.reader = new SAMFileReader(new File(samFilename));
    }
    
    public void setFASTARef(String filename) throws IOException {
        fastaRef = new FASTAFile(filename);
    }
    
    public void setMinMappingQual(int minMappingQual) {
        this.minMappingQual = minMappingQual;
    }

    public void setMinBaseQual(int minBaseQual) {
        this.minBaseQual = minBaseQual;
    }

    public void setFlagFilter(int flagFilter) {
        this.flagFilter = flagFilter;
    }

    public void setFlagRequired(int flagRequired) {
        this.flagRequired = flagRequired;
    }

    public SAMFileReader getReader() {
        return reader;
    }
    
    public void close() {
        reader.close();
        if (fastaRef != null) {
            try {
                fastaRef.close();
            } catch (IOException e) {
            }
        }
    }

    
    public Iterable<PileupPos> pileup() {
        return pileup(null, -1, -1);
    }        

    public Iterable<PileupPos> pileup(GenomeRegion region) {
        return pileup(region.ref, region.start, region.end);
    }
    public Iterable<PileupPos> pileup(String ref, int start, int end) {
        if (ref != null) {
            this.samIterator = reader.query(ref, start, end, false);
        } else {
            this.samIterator = reader.iterator();
        }
        
        return new Iterable<PileupPos>() {
            @Override
            public Iterator<PileupPos> iterator() {
                return new Iterator<PileupPos>() {
                    private final SortedMap<RefPos, PileupPos> pileupPos = new TreeMap<RefPos, PileupPos>();
                    private boolean initializeme = true;

                    private void populate() {
                        int curRefIdx = -1;
                        int curPos = -1;
                        while (samIterator.hasNext()) {
                            SAMRecord read = samIterator.next();
                            if (read.getMappingQuality() < minMappingQual) {
                                continue;
                            }
                            if (read.getReadUnmappedFlag()) {
                                continue;
                            }
                            if (read.getReadPairedFlag() && !read.getProperPairFlag()) {
                                continue;
                            }
                            
                            if ((read.getFlags() & flagFilter) > 0) {
                                continue;
                            }
                            
                            if ((read.getFlags() & flagRequired) != flagRequired) {
                                continue;
                            }
                            
                            if (curPos == -1) {
                                curPos = read.getAlignmentStart();
                                curRefIdx = read.getReferenceIndex();
                            }
                            populateRead(read);
                            if (read.getAlignmentStart() > curPos || read.getReferenceIndex() != curRefIdx) {
                                return;
                            }
                        }
                    }

                    private void populateRead(SAMRecord read) {
                        int refPos = read.getAlignmentStart(); // 1-based
                        int readPos = 0;
                        
                        boolean first = true;
                        for (CigarElement cigarEl:read.getCigar().getCigarElements()) {
                            CigarOperator cigarOp = cigarEl.getOperator();
                            int cigarLen = cigarEl.getLength();
                            
                            if (cigarOp == CigarOperator.M) {
                                for (int i=0; i<cigarLen; i++) {
                                    int qual = read.getBaseQualities()[readPos+i];
                                    if (qual >= minBaseQual) {
                                        addRefPosRead(read, refPos+i, readPos+i, cigarOp, 1, first);
                                    }
                                    first = false;
                                }
                                readPos += cigarLen;
                                refPos += cigarLen;
                            } else if (cigarOp == CigarOperator.I) {
                                addRefPosRead(read, refPos, readPos, cigarOp, cigarLen, false);
                                readPos += cigarLen;
                            } else if (cigarOp == CigarOperator.D) {
                                addRefPosRead(read, refPos, readPos, cigarOp, cigarLen, false);
                                refPos += cigarLen;
                            } else if (cigarOp == CigarOperator.N) {
                                refPos += cigarLen;
                            } else if (cigarOp == CigarOperator.S) {
                                readPos += cigarLen;
                            } // all other operations ignored (NHS=*)
                        }
                        markLast(read, refPos-1);
                    }

                    private void markLast(SAMRecord read, int refPos) {
                        RefPos refPosKey = new RefPos(read.getReferenceIndex(), refPos);
                        pileupPos.get(refPosKey).getLast().setEnd();
                    }
                    
                    private void addRefPosRead(SAMRecord read, int refPos, int readPos, CigarOperator cigarOp, int cigarLen, boolean isStart) {
                        RefPos refPosKey = new RefPos(read.getReferenceIndex(), refPos);
                        if (!pileupPos.containsKey(refPosKey)) {
                            if (fastaRef != null) {
                                try {
                                    pileupPos.put(refPosKey, new PileupPos(read.getReferenceIndex(), refPos, fastaRef.fetch(read.getReferenceName(), refPos-1, refPos)));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                pileupPos.put(refPosKey, new PileupPos(read.getReferenceIndex(), refPos));
                            }
                        }
                        pileupPos.get(refPosKey).addRead(read, readPos, cigarOp, cigarLen, isStart);
                    }
                    
                    @Override
                    public boolean hasNext() {
                        if (initializeme) {
                            populate();
                            initializeme = false;
                        }
                        return !pileupPos.isEmpty();
                    }

                    @Override
                    public PileupPos next() {
                        if (pileupPos.size()>0) {
                            RefPos first = pileupPos.firstKey();
                            PileupPos pp = pileupPos.remove(first);
                            populate();
                            return pp;
                        }
                        return null;
                    }

                    @Override
                    public void remove() {
                    }
                };
            }};
    }

}
