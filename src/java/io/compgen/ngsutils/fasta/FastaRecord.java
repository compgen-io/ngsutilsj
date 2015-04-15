package io.compgen.ngsutils.fasta;

import java.io.IOException;
import java.io.OutputStream;

public class FastaRecord {
    public final String name;
    public final String comment;
    public final String seq;
    
    public FastaRecord(String name, String seq) {
        this(name, seq, null);
    }
    
    public FastaRecord(String name, String seq, String comment) {
        this.name = name;
        this.seq = seq;
        this.comment = comment;
    }
    
    public String toString() {
        return ">"+name;
    }
    
    public void write(OutputStream os) throws IOException {
        write(os, -1);
    }
    
    public void write(OutputStream os, int wrap) throws IOException {
        String out = ">"+name;
        if (comment != null) {
            out += " " + comment;
        }

        out += "\n";
        
        os.write(out.getBytes());
        
        if (wrap > 0) {
            int acc = 0;
            for (int i=0; i<seq.length(); i++) {
                if (seq.charAt(i) == ' ' || seq.charAt(i) == '\n'  || seq.charAt(i) == '\r' || seq.charAt(i) == '\t') {
                    continue;
                }
                
                if (acc > 0 && acc % wrap == 0) {
                    os.write('\n');
                }

                os.write(seq.charAt(i));
                acc++;
            }
        } else {
            os.write(seq.getBytes());
        }
        
        os.write('\n');
    }
}
