package io.compgen.ngsutils.fasta;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class FastaWriter {
	final private OutputStream out;
    final private Charset charset;
    private String buf = "";
    private int wrap = 60;
    
    private boolean closed = false;
    private boolean inSeq = false;
    
    
    public FastaWriter() {
        this.out = System.out;
        this.charset = Charset.defaultCharset();
    }

    public FastaWriter(String filename) throws FileNotFoundException {
        this.out = new BufferedOutputStream(new FileOutputStream(filename));
        this.charset = Charset.defaultCharset();
    }

    public FastaWriter(OutputStream out) {
        this.out = new BufferedOutputStream(out);
        this.charset = Charset.defaultCharset();
    }

    public FastaWriter(Charset charset) {
        this.out = System.out;
        this.charset = charset;
    }

    public FastaWriter(OutputStream out, Charset charset) {
        this.out = new BufferedOutputStream(out);
        this.charset = charset;
    }

    public void close() throws IOException {
    	if (closed) { 
    		throw new IOException("Operation on a closed FastaWriter");
    	}
    	flush();
    	out.close();
    	closed = true;
    }
    
    private void flush() throws IOException {
    	if (closed) { 
    		throw new IOException("Operation on a closed FastaWriter");
    	}
    	if (buf.length() > 0) {
    		writeLine(buf);
    		buf = "";
        	out.flush();
    	}
    }
    
    private void writeLine(String s) throws IOException {
    	if (closed) { 
    		throw new IOException("Operation on a closed FastaWriter");
    	}
    	out.write((s + "\n").getBytes(charset));
    }
    
    public void seq(String seq) throws IOException {
    	if (closed) { 
    		throw new IOException("Operation on a closed FastaWriter");
    	}
    	if (!inSeq) { 
    		throw new IOException("You must write a header line before writing sequence");
    	}
    	
    	buf += seq;
    	while (buf.length() > wrap) {
    		writeLine(buf.substring(0, wrap));
    		buf = buf.substring(wrap);
    	}
    }
    
    public void start(String name) throws IOException {
    	start(name, null);
    }
    public void start(String name, String comment) throws IOException {
    	flush();
    	if (comment != null && comment.length() > 0) {
    		writeLine(">" + name + " " + comment);
    	} else {
    		writeLine(">" + name);
    	}
    	inSeq = true;
    }
	 
}
