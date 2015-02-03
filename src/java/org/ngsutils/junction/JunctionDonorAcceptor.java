package org.ngsutils.junction;

import org.ngsutils.annotation.GenomeSpan;
import org.ngsutils.bam.Strand;

public class JunctionDonorAcceptor implements Comparable<JunctionDonorAcceptor>{
    /**
     * 
     */
    public final String name;
    public final Strand strand;
    public final boolean read1;

    public JunctionDonorAcceptor(String name, Strand strand, boolean isRead1) {
        this.name = name;
        this.strand = strand;
        this.read1 = isRead1;
    }

    @Override
    public String toString() {
        return name + ", strand=" + strand + ", read=" + (read1?"R1": "R2");
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (read1 ? 1231 : 1237);
        result = prime * result + ((strand == null) ? 0 : strand.hashCode());
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
        JunctionDonorAcceptor other = (JunctionDonorAcceptor) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (read1 != other.read1) {
            return false;
        }
        if (strand != other.strand) {
            return false;
        }
        return true;
    }

    public int compareTo(JunctionDonorAcceptor o) {
        int val = GenomeSpan.parse(name, strand).compareTo(GenomeSpan.parse(o.name, o.strand));
     
        if (val != 0) {
            return val;
        }
        
        if (read1 && !o.read1) {
            return -1;
        } else if (read1 && o.read1){
            return 0;
        } else if (!read1 && !o.read1){
            return 0;
        }
        return 1;
    }
}
