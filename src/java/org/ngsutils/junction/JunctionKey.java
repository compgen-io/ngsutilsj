package org.ngsutils.junction;

import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Strand;

public class JunctionKey implements Comparable<JunctionKey> {
    /**
     * 
     */
    public final String name;
    public final Strand strand;
    public final boolean read1;
    
    public final JunctionDonorAcceptor donor;
    public final JunctionDonorAcceptor acceptor;

    public JunctionKey(String name, Strand strand) {
        this.name = name;
        this.strand = strand;
        this.read1 = false;

        String[] chrom_se = name.split(":");
        String[] se = chrom_se[1].split("-");
        
        if (strand == Strand.PLUS) {
            this.donor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[0], strand, false);
            this.acceptor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[1], strand, false);
        } else {
            this.donor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[1], strand, false);
            this.acceptor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[0], strand, false);
        }
    }

    public JunctionKey(String name, Strand strand, boolean isRead1) {
        this.name = name;
        this.strand = strand;
        this.read1 = isRead1;
        
        // name is in the form: chrom:start-end (zero-based, all in reference to + strand).
        // 
        // For (+) strand junctions:
        //     donor    -> chrom:start
        //     acceptor -> chrom:end
        // 
        // For (-) strand junctions:
        //     donor    -> chrom:end
        //     acceptor -> chrom:start
        //
        // For retained introns, these values will be the same!.
        
        String[] chrom_se = name.split(":");
        String[] se = chrom_se[1].split("-");
        
        if (strand == Strand.PLUS) {
            this.donor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[0], strand, isRead1);
            this.acceptor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[1], strand, isRead1);
        } else {
            this.donor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[1], strand, isRead1);
            this.acceptor = new JunctionDonorAcceptor(chrom_se[0]+":"+se[0], strand, isRead1);
        }
    }

    @Override
    public String toString() {
        return name;
        //+ ", strand=" + strand + ", read=" + (read1?"R1": "R2");
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
        JunctionKey other = (JunctionKey) obj;
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

    @Override
    public int compareTo(JunctionKey o) {
        int val = GenomeRegion.parse(name, strand).compareTo(GenomeRegion.parse(o.name, o.strand));
     
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

    public String getReadNum() {
        if (read1) {
            return "R1";
        }
        return "R2";
    }        
}