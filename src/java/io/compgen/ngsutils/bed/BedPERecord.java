package io.compgen.ngsutils.bed;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;

public class BedPERecord implements Comparable<BedPERecord> {
    final protected String name;
    final protected String score;
    final protected GenomeSpan coord1;
    final protected GenomeSpan coord2;
    final protected String[] extras;
    
    public BedPERecord(GenomeSpan coord1, GenomeSpan coord2) {
        this(coord1, coord2, null, "", null);
    }
    
    public BedPERecord(GenomeSpan coord1, GenomeSpan coord2, String name, String score) {
        this(coord1, coord2, name, score, null);
    }
    
    public BedPERecord(GenomeSpan coord1, GenomeSpan coord2, String name, String score, String[] extras) {
        this.coord1 = coord1;
        this.coord2 = coord2;
        this.name = name;
        this.score = score;
        this.extras = extras;
    }

    @Override
    public String toString() {
        return name;
    }
    
    public GenomeSpan getCoord1() {
        return coord1;
    }
    
    public GenomeSpan getCoord2() {
        return coord2;
    }
    
    public String[] getExtras() {
        return extras;
    }
    
    public void write(OutputStream os) throws IOException {
        List<String> outs = new ArrayList<String>();
        outs.add(coord1.ref);
        outs.add(""+coord1.start);
        outs.add(""+coord1.end);
        outs.add(coord2.ref);
        outs.add(""+coord2.start);
        outs.add(""+coord2.end);
        
        if (name != null) {
            outs.add(name);
            String s = ""+score;
            if (s.endsWith(".0")) {
                s = s.substring(0, s.length()-2);
            }
            outs.add(s);
            
            if (coord1.strand != Strand.NONE) {
                outs.add(coord1.strand.toString());
            } else {
                // always need to output a valid strand...
                outs.add(Strand.PLUS.toString());
            }
            if (coord2.strand != Strand.NONE) {
                outs.add(coord2.strand.toString());
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

    public String getScore() {
        return score;
    }

    public double getScoreAsDouble() {
    	try {
    		double s = Double.parseDouble(score);
    		return s;
    	} catch (NumberFormatException e) {}

    	return 0.0;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(BedPERecord o) {
    	int ret = coord1.compareTo(o.coord1);
        if (ret == 0) {
        	return coord2.compareTo(o.coord2);
        }
        return ret;
    }

}

