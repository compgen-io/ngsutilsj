package org.ngsutils.annotation;

import org.ngsutils.bam.Strand;

public class GenomeCoordinates implements Comparable<GenomeCoordinates> {
    final public String ref;
    final public int start;
    final public int end;
    final public Strand strand;
    final public String[] annotations;
    
    public GenomeCoordinates(String ref, int start, int end, Strand strand, String[] annotations) {
        super();
        this.ref = ref;
        this.start = start;
        this.end = end;
        this.strand = strand;
        this.annotations = annotations;
    }
    public GenomeCoordinates(String ref, int start, Strand strand,String[] annotations) {
        super();
        this.ref = ref;
        this.start = start;
        this.end = start;
        this.strand = strand;
        this.annotations = annotations;
    }
    

    public boolean contains(int qstart, int qend, Strand qstrand) { 
        return contains(qstart, qend, qstrand, true);
    }
    
    public boolean contains(int qstart, int qend, Strand qstrand, boolean onlyWithin){
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
        return ref+":"+Integer.toString(start)+"-"+Integer.toString(end);
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
        GenomeCoordinates other = (GenomeCoordinates) obj;
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
    public int compareTo(GenomeCoordinates o) {
        if (!ref.equals(o)) {
            return ref.compareTo(o.ref);
        }
        if (start == o.start) {
            return Integer.compare(end, o.end);
        }
        return Integer.compare(start, o.start);
    }
}
