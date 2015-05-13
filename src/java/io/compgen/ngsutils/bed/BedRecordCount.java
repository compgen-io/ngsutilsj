package io.compgen.ngsutils.bed;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.bam.Strand;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class BedRecordCount extends BedRecord {
    private int count = 0;
    
    public BedRecordCount(BedRecord record) {
        super(record.coord, record.name, record.score, record.extras);
    }

    public int getCount() {
        return count;
    }
    
    public void incr() {
        this.count++;
    }
    @Override
    public String[] toStringArray() {
        String s = ""+score;
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length()-2);
        }

        return new String[] { name, s, ""+count };
    }

    @Override
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
        
        outs.add(""+count);

        if (extras != null) {
            for (String extra:extras) {
                outs.add(extra);
            }
        }

        os.write((StringUtils.join("\t", outs) + "\n").getBytes());
    }
    
    
}
