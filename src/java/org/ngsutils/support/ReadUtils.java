package org.ngsutils.support;

import net.sf.samtools.SAMRecord;

import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;

public class ReadUtils {
    public static Strand getFragmentEffectiveStrand(SAMRecord read, Orientation orient) {
        if (read.getReadUnmappedFlag()) {
            return null;
        }
        if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
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
