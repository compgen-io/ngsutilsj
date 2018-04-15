package io.compgen.ngsutils.vcf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import io.compgen.ngsutils.tabix.BGZFile;
import io.compgen.ngsutils.tabix.BGZInputStream;

public class VCFReader {
	protected BufferedReader in;
	protected VCFHeader header=null;
	private boolean closed = false;
	
	public VCFReader(String filename) throws IOException, VCFParseException {
        if (filename.equals("-")) {
            in = new BufferedReader(new InputStreamReader(System.in));
        } else if (BGZFile.isBGZFile(filename)) {
                in = new BufferedReader(new InputStreamReader(new BGZInputStream(filename)));               
        } else if (isGZipFile(filename)){
                in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
        } else {
            in = new BufferedReader(new FileReader(filename));
        }

//        if (filename.equals("-")) {
//            in = new BufferedReader(new InputStreamReader(System.in));
//        } else if (filename.endsWith(".gz") || filename.endsWith(".bgz") ||filename.endsWith(".bgzf")) {
//            if (BGZFile.isBGZFile(filename)) {
//                in = new BufferedReader(new InputStreamReader(new BGZInputStream(filename)));               
//            } else {
//                in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
//            }
//        } else {
//            in = new BufferedReader(new FileReader(filename));
//        }

        readHeader();
	}

	public static boolean isGZipFile(String filename) throws IOException {
	    InputStream is = new FileInputStream(filename);
        byte[] magic = new byte[2];
        is.read(magic);
        is.close();
        return Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B});// need to cast 0x8b because it is a neg. num in 2-complement
	}
	
	public VCFReader(InputStream stream) throws IOException, VCFParseException {
		in = new BufferedReader(new InputStreamReader(stream));
		readHeader();
	}

	private void readHeader() throws IOException, VCFParseException {
		String format=null;
		List<String> lines = new ArrayList<String>();
		String headerLine = null;
		while (headerLine == null) {
			String line = in.readLine();
			//System.err.println(line);
			if (line == null || line.equals("")) {
				throw new IOException("Bad VCF header? Missing header line?");
			}
			if (line.startsWith("##fileformat=")) {
				format = line;
			} else if (line.startsWith("##")) {
				lines.add(line);
			} else if (line.startsWith("#CHROM\t")) {
				headerLine = line;
			}
		}
		
		header = new VCFHeader(format, lines, headerLine);
	}

	public VCFHeader getHeader() {
		return header;
		
	}
	
	public void close() throws IOException {
		if (!closed) {			
			closed = true;
			in.close();
		}
	}
	
	public Iterator<VCFRecord> iterator() {
		return new Iterator<VCFRecord> () {

			VCFRecord next = null;
			boolean first = true;
			
			@Override
			public boolean hasNext() {
				if (first) {
					populateNext();
				}
				return next != null;
			}

			@Override
			public VCFRecord next() {
				if (first) {
					populateNext();
				}
				VCFRecord cur = next;
				populateNext();
				return cur;
			}

			private void populateNext() {
				first = false;
				try {
					if (closed) {
						next = null; 
						return;
					}
					
					String line = in.readLine();
					if (line == null) {
						next = null;
						return;
					}
					
					next = VCFRecord.parseLine(line);
					
					
				} catch (IOException | VCFParseException e) {
					next = null;
					e.printStackTrace(System.err);
				}
			}
		};
	}
	
}
