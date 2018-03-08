package io.compgen.ngsutils.vcf;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class VCFWriter {
	final private VCFHeader header;
	protected boolean headerWritten = false;
	protected OutputStream out;
	
	// should we remove INFO/FORMAT/FILTER/dbSNPId values?
	protected boolean strip = false;
	
   public VCFWriter(String filename, VCFHeader header) throws FileNotFoundException {
       this(filename, header, false);
   }
    public VCFWriter(OutputStream stream, VCFHeader header) throws FileNotFoundException {
        this(stream, header, false);
    }

	public VCFWriter(String filename, VCFHeader header, boolean strip) throws FileNotFoundException {
		this.header = header;
		out = new FileOutputStream(filename);
        this.strip = strip;
	}

	public VCFWriter(OutputStream stream, VCFHeader header, boolean strip) throws FileNotFoundException {
		this.header = header;
		out = stream;
        this.strip = strip;
	}

	public void close() throws IOException {
		out.flush();
		out.close();
	}
	
	public void write(VCFRecord record) throws IOException {
		if (!headerWritten) {
			header.write(out, true, strip);
			headerWritten = true;
		}
		
		record.write(out, strip);
	}
}
