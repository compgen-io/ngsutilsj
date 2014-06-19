package org.ngsutils.bam.support;

import net.sf.samtools.SAMRecord;

import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;

public class ReadUtils {
    public static final int READ_PAIRED_FLAG = 0x1;
    public static final int PROPER_PAIR_FLAG = 0x2;
    public static final int READ_UNMAPPED_FLAG = 0x4;
    public static final int MATE_UNMAPPED_FLAG = 0x8;
    public static final int READ_STRAND_FLAG = 0x10;
    public static final int MATE_STRAND_FLAG = 0x20;
    public static final int FIRST_OF_PAIR_FLAG = 0x40;
    public static final int SECOND_OF_PAIR_FLAG = 0x80;
    public static final int NOT_PRIMARY_ALIGNMENT_FLAG = 0x100;
    public static final int READ_FAILS_VENDOR_QUALITY_CHECK_FLAG = 0x200;
    public static final int DUPLICATE_READ_FLAG = 0x400;
    public static final int SUPPLEMENTARY_ALIGNMENT_FLAG = 0x800;

    /**
     * Calculates the effective orientation for a given fragment. This is useful for strand-specific operations
     * where you want to filter out reads that aren't in the correct orientation.
     * 
     * @param read
     * @param orient - enum: RF, FR, or unstranded
     * @return enum Strand: PLUS, MINUS (null for unmapped)
     */
    public static Strand getFragmentEffectiveStrand(SAMRecord read, Orientation orient) {
        if (read.getReadUnmappedFlag()) {
            return null;
        }
        if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
            // unpaired or first read in a pair
            if (orient == Orientation.FR || orient == Orientation.UNSTRANDED) {
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            }
        } else {
            // paired end and second read...
            if (orient == Orientation.FR || orient == Orientation.UNSTRANDED) {
                // this assumes read1 and read2 are sequenced in opposite
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            }
        }
    }
}
