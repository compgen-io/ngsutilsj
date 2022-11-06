package io.compgen.ngsutils.cli.bed;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedPEReader;
import io.compgen.ngsutils.bed.BedPERecord;
import io.compgen.ngsutils.bed.BedRecord;

@Command(name="bedpe-tobed", desc="Convert a BEDPE file to a BED file by combining coordinates (default) or splitting the coordinates", category="bed", doc="Note: only non-discordant records can be combined.")
public class BedPEToBed extends AbstractOutputCommand {
    
    private String filename = null;
    private int maxDistance = 0;
    private boolean split = false;
    private boolean first = false;
    private boolean second = false;
    
    @Option(desc = "Split records into two BED lines", name="split")
    public void setSplit(boolean split) {
    	this.split = split;
    }

    @Option(desc = "Write the first record only", name="first")
    public void setFirst(boolean first) {
    	this.first = first;
    }

    @Option(desc = "Write the second record only", name="second")
    public void setSecond(boolean second) {
    	this.second = second;
    }

    @Option(desc = "Maximum allowed distance between records (-1 to disable)", name="max", defaultValue="10000")
    public void setMaxDistance(int maxDistance) {
        this.maxDistance = maxDistance;
    }
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
    	int i = 0;
    	if (first) {
    		i++;
    	}
    	if (second) {
    		i++;
    	}
    	if (split) {
    		i++;
    	}
    	
    	if (i > 1) {
    		throw new CommandArgumentException("You can only specify one of: --first --second --split");
    	}
    	
        for (BedPERecord record: IterUtils.wrap(BedPEReader.readFile(filename))) {
            
        	GenomeSpan coord1 = record.getCoord1();
            GenomeSpan coord2 = record.getCoord2();
            
            if (split) {
            	new BedRecord(new GenomeSpan(coord1.ref, coord1.start, coord1.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
            	new BedRecord(new GenomeSpan(coord2.ref, coord2.start, coord2.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
            } else if (first) {
                new BedRecord(new GenomeSpan(coord1.ref, coord1.start, coord1.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
            } else if (second) {
                new BedRecord(new GenomeSpan(coord2.ref, coord2.start, coord2.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
            } else {
	            if (isDiscordant(coord1, coord2, maxDistance)) {
	            	continue;            	
	            }
	            if (coord1.compareTo(coord2) <  0) { // coord1 is less
	            	new BedRecord(new GenomeSpan(coord1.ref, coord1.start, coord2.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
	            } else {
	            	new BedRecord(new GenomeSpan(coord1.ref, coord2.start, coord1.end), record.getName(), record.getScoreAsDouble(), record.getExtras()).write(out);
	            }
            }
        }
    }
    
    public static boolean isDiscordant(GenomeSpan coord1, GenomeSpan coord2, int maxDist) {
    	if (coord1.ref.equals(coord2.ref)) {
    		if (coord1.compareTo(coord2) <  0) { // coord1 is less
    			if (maxDist == -1 || coord2.start - coord1.end <= maxDist) {
    				// concordant reads are on different strands
    				return coord1.strand != Strand.NONE && coord2.strand != Strand.NONE && coord2.strand.equals(coord1.strand); 
    			}
    		} else {
    			if (maxDist == -1 || coord1.start - coord2.end <= maxDist) { // coord2 is less or equal
    				// concordant reads are on different strands
    				return coord1.strand != Strand.NONE && coord2.strand != Strand.NONE && coord2.strand.equals(coord1.strand); 
    			}
    		}
    	}
    	return true;
    }
}
