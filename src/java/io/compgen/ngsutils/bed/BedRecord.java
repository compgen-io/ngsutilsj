package io.compgen.ngsutils.bed;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.Annotation;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

public class BedRecord implements Annotation, Comparable<BedRecord> {
    final protected String name;
    final protected double score;
    final protected GenomeSpan coord;
    final protected String[] extras;
    
    public BedRecord(GenomeSpan coord) {
        this(coord, null, 0, null);
    }
    
    public BedRecord(GenomeSpan coord, String name) {
        this(coord, name, 0, null);
    }

    public BedRecord(GenomeSpan coord, String name, double score) {
        this(coord, name, score, null);
    }
    
    public BedRecord(GenomeSpan coord, String name, double score, String[] extras) {
        this.coord = coord;
        this.name = name;
        this.score = score;
        this.extras = extras;
    }

    @Override
    public String[] toStringArray() {
        String s = ""+score;
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length()-2);
        }

        return new String[] { name, s };
    }

    @Override
    public String toString() {
        return name;
    }
    
    public GenomeSpan getCoord() {
        return coord;
    }
    
    public String[] getExtras() {
        return extras;
    }
    
    public void write(OutputStream os) throws IOException {
        write(os, false);
    }
    public void write(OutputStream os, boolean forceScoreInt) throws IOException {
        List<String> outs = new ArrayList<String>();
        outs.add(coord.ref);
        outs.add(""+coord.start);
        outs.add(""+coord.end);
        
        if (name != null) {
            outs.add(name);
            if (forceScoreInt) {
                outs.add(""+(int)score);
            } else {
                String s = ""+score;
                if (s.endsWith(".0")) {
                    s = s.substring(0, s.length()-2);
                }
                outs.add(s);
            }
            
            if (coord.strand != Strand.NONE) {
                outs.add(coord.strand.toString());
            } else {
                // always need to output a valid strand...
                outs.add(Strand.PLUS.toString());
            }
        }
        
        if (extras != null) {
            for (String extra:extras) {
                outs.add(extra);
            }
        }

        os.write((StringUtils.join("\t", outs) + "\n").getBytes());
    }

    public double getScore() {
        return score;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(BedRecord o) {
        return coord.compareTo(o.coord);
    }

	public BedRecord clone(String newName) {
		return new BedRecord(this.coord, newName, this.score, this.extras);
	}

	public BedRecord clone(GenomeSpan newcoord) {
		return new BedRecord(newcoord, this.name, this.score, this.extras);
	}

}

