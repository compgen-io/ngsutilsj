package io.compgen.ngsutils.vcf;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class VCFWriter {
	final private VCFHeader header;
	protected boolean headerWritten = false;
	protected OutputStream out;
	
	public VCFWriter(String filename, VCFHeader header) throws FileNotFoundException {
		this.header = header;
		out = new FileOutputStream(filename);
	}

	public VCFWriter(OutputStream stream, VCFHeader header) throws FileNotFoundException {
		this.header = header;
		out = stream;
	}

	public void close() throws IOException {
		out.flush();
		out.close();
	}
	
	public void write(VCFRecord record) throws IOException {
		if (!headerWritten) {
			header.write(out, true);
			headerWritten = true;
		}
		
		record.write(out);
	}
}
