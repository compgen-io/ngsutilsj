package org.ngsutils.annotation;

import org.ngsutils.bam.Strand;

public class GenomeRegion implements Comparable<GenomeRegion> {
    final public String ref;
    final public int start;
    final public int end;
    final public Strand strand;
    
    public GenomeRegion(String ref, int start, int end, Strand strand){
        super();
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.strand = strand;
    }

    public GenomeRegion(String ref, int start, Strand strand){
        super();
        this.ref = ref;
        this.start = start;
        this.end = start;
        this.strand = strand;
    }
    
    public boolean contains(GenomeRegion coord, boolean onlyWithin) {
        return contains(coord.ref, coord.start, coord.end, coord.strand, onlyWithin);
    }

    public boolean contains(GenomeRegion coord) {
        return contains(coord, true);
    }

    public boolean contains(String qref, int qstart, int qend, Strand qstrand) { 
        return contains(qref, qstart, qend, qstrand, true);
    }
    
    public boolean contains(String qref, int qstart, int qend, Strand qstrand, boolean onlyWithin){
        if (!ref.equals(qref)) {
            return false;
        }
        if (qstrand != Strand.NONE && strand != Strand.NONE && qstrand != strand) {
            return false;            
        }
        
        if (start <= qstart && qstart <= end) {
            return true;
        }
        if (start <= qend && qend <= end) {
            return true;
        }
        
        if (!onlyWithin) {
            if (qstart <= start && start <= qend &&  qstart <= end && end <=qend) {
                return true;
            }
        }
        return false;
    }
    
    @Override                                                                                                                                                                                                                                                     
    public String toString() {
        if (start == end) {
            return ref+":"+Integer.toString(start);
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
}
