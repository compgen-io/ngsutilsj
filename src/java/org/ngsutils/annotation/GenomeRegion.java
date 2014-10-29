package org.ngsutils.annotation;

import java.util.ArrayList;
import java.util.List;

import net.sf.samtools.AlignmentBlock;
import net.sf.samtools.SAMRecord;

import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;
import org.ngsutils.bam.support.ReadUtils;

public class GenomeRegion implements Comparable<GenomeRegion> {
    final public String ref;
    final public int start; // genome regions are 0-based
    final public int end;
    final public Strand strand;
    
    public GenomeRegion(String ref, int start, int end, Strand strand){
        super();
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.strand = strand;
    }

    public GenomeRegion(String ref, int start, int end){
        super();
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.strand = Strand.NONE;
    }

    public GenomeRegion(String ref, int start, Strand strand){
        super();
        this.ref = ref;
        this.start = start;
        this.end = start;
        this.strand = strand;
    }
    
    public GenomeRegion(String ref, int start){
        super();
        this.ref = ref;
        this.start = start;
        this.end = start;
        this.strand = Strand.NONE;
    }
    
    public static List<GenomeRegion> getReadAlignmentRegions(SAMRecord read, Orientation orient) {
        List<GenomeRegion> out = new ArrayList<GenomeRegion>();
        for (AlignmentBlock block:read.getAlignmentBlocks()) {
            if (orient == Orientation.UNSTRANDED) {
                out.add(new GenomeRegion(read.getReferenceName(), block.getReferenceStart()-1,  block.getReferenceStart()-1+block.getLength(), Strand.NONE));
            } else {
                out.add(new GenomeRegion(read.getReferenceName(), block.getReferenceStart()-1,  block.getReferenceStart()-1+block.getLength(), ReadUtils.getFragmentEffectiveStrand(read, orient)));
            }
        }
        return out;
    }
    
    // Strings are chrom:start-end, where start is the 1-based start position.
    public static GenomeRegion parse(String str) {
        return parse(str, Strand.NONE, false);
    }
    
    public static GenomeRegion parse(String str, boolean zero) {
        return parse(str, Strand.NONE, zero);
    }
    
    public static GenomeRegion parse(String str, Strand strand) {
        return parse(str, strand, false);
    }
    
    public static GenomeRegion parse(String str, Strand strand, boolean zero) {
        if (str.indexOf(':') <= 0) {
            return null;
        }

        String ref = str.substring(0,str.indexOf(':'));
        String startend = str.substring(str.indexOf(':')+1);

        if (startend.indexOf('-') == 0) {
            return null;
        } else if (startend.indexOf('-') == -1) {
            int pos = Integer.parseInt(startend);
            return new GenomeRegion(ref, pos, strand);
        } else {
            int start = Integer.parseInt(startend.substring(0, startend.indexOf('-')));
            int end = Integer.parseInt(startend.substring(startend.indexOf('-')+1));
            
            if (!zero) {
                start = start - 1; // if this isn't zero based, then adjust the start position
            }
            
            return new GenomeRegion(ref, start, end, strand);
        }
    }
    
    public boolean contains(GenomeRegion coord) {
        return contains(coord.ref, coord.start, coord.end, coord.strand, true);
    }

    public boolean overlaps(GenomeRegion coord) {
        return contains(coord.ref, coord.start, coord.end, coord.strand, false);
    }

    public boolean contains(String qref, int qstart, int qend, Strand qstrand) { 
        return contains(qref, qstart, qend, qstrand, true);
    }
    
    public boolean overlaps(String qref, int qstart, int qend, Strand qstrand) { 
        return contains(qref, qstart, qend, qstrand, false);
    }
    
    protected boolean contains(String qref, int qstart, int qend, Strand qstrand, boolean onlyWithin){
        if (!ref.equals(qref)) {
            return false;
        }
        if (qstrand != Strand.NONE && strand != Strand.NONE && qstrand != strand) {
            return false;            
        }
        
        boolean startWithin = false;
        boolean endWithin = false;
        
        if (start <= qstart && qstart < end) {
            startWithin = true;
        }
        if (start < qend && qend <= end) {
            endWithin = true;
        }
        
        if (onlyWithin) {
            if (startWithin && endWithin) {
                return true;
            }
            return false;
        }
        
        if (!onlyWithin) {
            if (startWithin || endWithin) {
                return true;
            }
            if (qstart <= start && start <= qend &&  qstart <= end && end <=qend) {
                // query region spans the entirety of the this region
                return true;
            }
        }
        return false;
    }
    
    @Override                                                                                                                                                                                                                                                     
    public String toString() {
        if (start == end) {
            if (strand==Strand.NONE) {
                return ref+":"+Integer.toString(start);
            }
            return ref+strand+":"+Integer.toString(start);
        }
        if (strand==Strand.NONE) {
            return ref+":"+Integer.toString(start)+"-"+Integer.toString(end);
        }
        return ref+strand+":"+Integer.toString(start)+"-"+Integer.toString(end);
    }

    @Override                                                                                                                                                                                                                                                     
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + end;
        result = prime * result + ((ref == null) ? 0 : ref.hashCode());
        result = prime * result + start;
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
        GenomeRegion other = (GenomeRegion) obj;
        if (end != other.end) {
            return false;
        }
        if (ref == null) {
            if (other.ref != null) {
                return false;
            }
        } else if (!ref.equals(other.ref)) {
            return false;
        }
        if (start != other.start) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(GenomeRegion o) {
        if (!ref.equals(o.ref)) {
            return ref.compareTo(o.ref);
        }

        if (start < o.start) {
            return -1;
        } else if (start > o.start) {
            return 1;
        } else {
            if (end < o.end) {
                return -1;
            } else if (end > o.end) {
                return 1;
            }
            if (strand == Strand.NONE || o.strand == Strand.NONE || strand == o.strand) {
                return 0;
            } else if (strand == Strand.PLUS) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public int length() {
        return end - start;
    }

    public static GenomeRegion getReadStartPos(SAMRecord read) {
        String ref = read.getReferenceName();
        int pos = read.getAlignmentStart()-1;
        return new GenomeRegion(ref, pos);
    }
}
