package io.compgen.ngsutils.annotation;

import java.util.ArrayList;
import java.util.List;

import htsjdk.samtools.AlignmentBlock;
import htsjdk.samtools.SAMRecord;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bam.support.ReadUtils;


/*
 * There be dragons here... off by one dragons.
 */

public class GenomeSpan implements Comparable<GenomeSpan> {
    final public String ref;
    final public int start; // genome regions are stored 0-based
    final public int end;
    final public Strand strand;
    
    public GenomeSpan(String ref, int start, int end, Strand strand) {
        super();
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.strand = strand;
    }

    public GenomeSpan(String ref, int start){
        this(ref, start, Strand.NONE);
    }
    
    public GenomeSpan(String ref, int start, int end){
        this(ref, start, end, Strand.NONE);
    }

    public GenomeSpan(String ref, int start, Strand strand){
        // TODO: Should the end be start + 1??? Check how this plays with comparisons.
        this(ref, start, start+1, strand);
    }
    
    public GenomeSpan clone() {
        return clone(null);
    }

    public GenomeSpan clone(Strand strand) {
        return new GenomeSpan(this.ref, this.start, this.end, (strand == null ? this.strand: strand));
    }
    
    public static List<GenomeSpan> getReadAlignmentRegions(SAMRecord read, Orientation orient) {
        List<GenomeSpan> out = new ArrayList<GenomeSpan>();
        for (AlignmentBlock block:read.getAlignmentBlocks()) {
            if (orient == Orientation.UNSTRANDED) {
                out.add(new GenomeSpan(read.getReferenceName(), block.getReferenceStart()-1,  block.getReferenceStart()-1+block.getLength(), Strand.NONE));
            } else {
                out.add(new GenomeSpan(read.getReferenceName(), block.getReferenceStart()-1,  block.getReferenceStart()-1+block.getLength(), ReadUtils.getFragmentEffectiveStrand(read, orient)));
            }
        }
        return out;
    }
    
    // Strings are chrom:start-end, where start is the 1-based start position.
    public static GenomeSpan parse(String str) {
        return parse(str, Strand.NONE, false);
    }
    
    public static GenomeSpan parse(String str, boolean zero) {
        return parse(str, Strand.NONE, zero);
    }
    
    public static GenomeSpan parse(String str, Strand strand) {
        return parse(str, strand, false);
    }
    
    public static GenomeSpan parse(String str, Strand strand, boolean zero) {
    	// allow for an escaped colon in the chrom name (custom FASTA files)
        int colonPos = str.indexOf(':');
        while (colonPos > 1 && str.charAt(colonPos-1) == '\\') {
        	str = str.substring(0, colonPos-1) + str.substring(colonPos);
        	colonPos = str.indexOf(':', colonPos); // we shrunk the str, so we don't need to add one here.
        }

        if (colonPos <= 0) {
            return new GenomeSpan(str, -1, -1);
        }

        
        String ref = str.substring(0,colonPos);
        String startend = str.substring(colonPos+1);

        if (startend.indexOf('-') == 0) {
            return null;
        } else if (startend.indexOf('-') == -1) {
            int pos = Integer.parseInt(startend);
            if (!zero) {
                pos = pos - 1; // if this isn't zero based INPUT, then adjust the start position
            }
            return new GenomeSpan(ref, pos, strand);
        } else {
            int start = Integer.parseInt(startend.substring(0, startend.indexOf('-')));
            int end = Integer.parseInt(startend.substring(startend.indexOf('-')+1));
            
            if (!zero) {
                start = start - 1; // if this isn't zero based INPUT, then adjust the start position
            }
            
            return new GenomeSpan(ref, start, end, strand);
        }
    }
    
    public boolean contains(GenomeSpan coord) {
        return contains(coord.ref, coord.start, coord.end, coord.strand, true);
    }

    public boolean overlaps(GenomeSpan coord) {
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
        
        if (start < 0 || qstart == -1) {
            // this is a whole-chrom span, so if the ref matches, return true
            return true;
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
        return toString(false);
    }

    public String toString(boolean oneBasedStart) {
        if (start < 0) {
            return ref;
        }
        if (start == end) {
            if (strand==Strand.NONE) {
                return ref+":"+Integer.toString(start + (oneBasedStart ? 1: 0));
            }
            return ref+strand+":"+Integer.toString(start + (oneBasedStart ? 1: 0));
        }
        if (strand==Strand.NONE) {
            return ref+":"+Integer.toString(start + (oneBasedStart ? 1: 0))+"-"+Integer.toString(end);
        }
        return ref+strand+":"+Integer.toString(start + (oneBasedStart ? 1: 0))+"-"+Integer.toString(end);
    }

    @Override                                                                                                                                                                                                                                                     
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + end;
        result = prime * result + ((ref == null) ? 0 : ref.hashCode());
        result = prime * result + start;
        result = prime * result + ((strand == Strand.PLUS) ? ((strand == Strand.MINUS) ? 3 : 5) : 7);
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
        
        GenomeSpan other = (GenomeSpan) obj;
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
        if (strand != other.strand) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(GenomeSpan o) {
        if (!ref.equals(o.ref)) {
            return StringUtils.naturalCompare(ref, o.ref);
        }

        if (start < o.start) {
            // whole chrom length spans will always be first
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

    public static GenomeSpan getReadStartPos(SAMRecord read) {
        return GenomeSpan.getReadStartPos(read, null);
    }

    /**
     * Returns the 5' start site of a read (strand-specific, not what is included in
     * BAM file for minus strand reads).
     * @param read
     * @param orient
     * @return
     */
    
    public static GenomeSpan getReadStartPos(SAMRecord read, Orientation orient) {
        String ref = read.getReferenceName();
        int pos;
        Strand strand;

        if (read.getReadNegativeStrandFlag()) {
            pos = read.getAlignmentEnd();
        } else {
            pos = read.getAlignmentStart()-1;
        }
        
        if (orient != null) {
            strand = ReadUtils.getFragmentEffectiveStrand(read, orient);
        } else if (read.getReadNegativeStrandFlag()) {
            strand = Strand.MINUS;
        } else {
            strand = Strand.PLUS;
        }

        return new GenomeSpan(ref, pos, strand);
    }

    public GenomeSpan getStartPos() {
        return new GenomeSpan(ref, start, start + 1, strand);
    }
    public GenomeSpan getEndPos() {
        return new GenomeSpan(ref, end-1, end, strand);
    }

    public GenomeSpan extend5(int len) {
        int newstart = start;
        int newend = end;
        
        if (strand == Strand.PLUS || strand == Strand.NONE) {
            newstart = start - len;
        } else {
            newend = end + len;
        }
        return new GenomeSpan(ref, newstart, newend, strand);
    }

    public GenomeSpan extend3(int len) {
        int newstart = start;
        int newend = end;
        
        if (strand == Strand.PLUS || strand == Strand.NONE) {
            newend = end + len;
        } else {
            newstart = start - len;
        }
        return new GenomeSpan(ref, newstart, newend, strand);
    }

    public GenomeSpan combine(GenomeSpan target) {
        if (!ref.equals(target.ref)) {
            return null;
        }
        
        int newstart = Math.min(start, target.start);
        int newend = Math.max(end,  target.end);
        
        Strand newstrand = strand;
        
        if (strand != target.strand) {
            newstrand = Strand.NONE;
        }
        
        return new GenomeSpan(ref, newstart, newend, newstrand);
    }

    public int distanceTo(GenomeSpan coord) throws BadReferenceException {
        if (!ref.equals(coord.ref)) {
            throw new BadReferenceException("References do not match!");
        }
        
        if (start > coord.end) {
            return -(start - coord.end);
        }
        
        if (end < coord.start) {
            return coord.start - end;
        }
        
        return 0;
    }
}
