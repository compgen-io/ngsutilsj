package org.ngsutils.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class FastqRead {
	private String name;
	private String comment;
	private String seq;
	private String qual;
	
	public FastqRead(String name, String comment, String seq, String qual) {
		this.name = name;
		if (comment != null && !comment.equals("")) {
			this.comment = comment;
		} else {
			this.comment = null;
		}
		this.seq = seq;
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
	
	static public FastqRead read(BufferedReader in) {
		try {
			String name = in.readLine();
			if (name == null) {
				return null;
			}
			name = name.substring(1); // strip the @
			String comment = null;
			if (name.indexOf(' ') > -1) {
				name = name.substring(0, name.indexOf(' '));
				comment = name.substring(name.indexOf(' ') + 1);
			}
			String seq = in.readLine();
			if (seq == null) {
				return null;
			}
			in.readLine();
			String qual = in.readLine();
			if (qual == null) {
				return null;
			}
			return new FastqRead(name, comment, seq, qual);
		} catch (IOException e) {
			return null;
		}
	}	
	
public void write(OutputStream out) throws IOException {
		String rec;
		if (comment != null) {
			rec = "@"+name+" "+comment+"\n"+seq+"\n+\n"+qual+"\n";
		} else {
			rec = "@"+name+"\n"+seq+"\n+\n"+qual+"\n";
		}
		out.write(rec.getBytes(Charset.forName("ASCII")));
	}
}
