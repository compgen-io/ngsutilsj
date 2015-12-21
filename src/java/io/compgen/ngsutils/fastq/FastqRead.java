package io.compgen.ngsutils.fastq;

import java.io.IOException;
import java.io.OutputStream;

public class FastqRead {
	private String name;
	private String comment;
	private String seq;
	private String qual;
	
    public FastqRead(String name, String seq, String qual) {
        this(name, seq, qual, null);
    }
    public FastqRead(String name, String seq, String qual, String comment) {
        this.name = name;
        if (comment != null && !comment.equals("")) {
            this.comment = comment;
        } else {
            this.comment = null;
        }
        this.seq = seq.toUpperCase();
        this.qual = qual;
    }

	public String getName() {
		return name;
	}
	public String getComment() {
		return comment;
	}
	public String getSeq() {
		return seq;
	}
	public String getQual() {
		return qual;
	}
	
	public void write(OutputStream out) throws IOException {
	    // technically the seq and qual can be wrapped, but it's rarely used and not recommended.
	    // so, that's not implemented here.
	    
		String rec;
		if (comment != null) {
			rec = "@"+name+" "+comment+"\n"+seq+"\n+\n"+qual+"\n";
		} else {
			rec = "@"+name+"\n"+seq+"\n+\n"+qual+"\n";
		}
		out.write(rec.getBytes());
	}

	// needed to reset name of a read if there is a pair flag (/1, /2)
    public void setName(String name) {
        this.name = name;
    }
    // needed to reset name of a read if there is a pair flag (/1, /2)
    public void setComment(String comment) {
        this.comment = comment;
    }
}
