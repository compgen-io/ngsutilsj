package io.compgen.ngsutils.tdf;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import io.compgen.common.Pair;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

/**
 * For this class, tab-delimited text files can have lines that are commented, and have an optional header line. The header line may or may not be commented.
 * Commented lines start with a meta character (# by default).
 * 
 * @author mbreese
 *
 */

public class TabDelimitedFile {
	protected String filename;
	protected char meta;
	protected boolean hasHeader;
	protected boolean headerIsCommented;
    	
	private String[] headerNames = null;
        
	public TabDelimitedFile(String filename) throws IOException {
		this(filename, false, false, '#');
	}
	public TabDelimitedFile(String filename, boolean hasHeader) throws IOException {
		this(filename, hasHeader, false, '#');
	}
	public TabDelimitedFile(String filename, char meta) throws IOException {
			this(filename, false, false, meta);
	}
	public TabDelimitedFile(String filename, boolean hasHeader, boolean headerIsCommented) throws IOException {
			this(filename, hasHeader, headerIsCommented, '#');
	}

	public TabDelimitedFile(String filename, boolean hasHeader, boolean headerIsCommented, char meta) throws IOException {
		this.filename = filename;
		this.hasHeader = hasHeader;
		this.headerIsCommented = headerIsCommented;
		this.meta = meta;
		
		if (headerIsCommented && meta == 0) {
			throw new IOException("If the header is commented, meta must be set");
		}
		
		if (hasHeader) {
			readHeader();
		}
		
	}
	
	@SuppressWarnings("resource")
	public Pair<InputStream, FileChannel> openFile() throws IOException {
		if (!this.filename.equals("-")) {
	        FileInputStream fis = new FileInputStream(this.filename);
	        byte[] magic = new byte[2];
	        fis.read(magic);
	        fis.close();
	
	        FileInputStream fis2 = new FileInputStream(this.filename);
	        if (Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B})) {  /// need to cast 0x8b because it is a neg. num in 2-complement
	        	return new Pair<InputStream, FileChannel>(new GzipCompressorInputStream(fis2, true), fis2.getChannel());
	        }
	    	return new Pair<InputStream, FileChannel>(fis2, fis2.getChannel());
		}
    	return new Pair<InputStream, FileChannel>(System.in, null);
	}
	
	/**
	 * return all of the commented lines in a file
	 * 
	 * @return
	 * @throws IOException
	 */
	public String readComments()  throws IOException {
		Pair<InputStream, FileChannel> tup = openFile();
		
		StringLineReader reader = new StringLineReader(tup.one);
		Iterator<String> it = reader.iterator();

		List<String> ret = new ArrayList<String>();
		
		
		while (it.hasNext()) {
			String line = it.next();
			
			if (line.charAt(0) == meta) {
				ret.add(line);
			} else if (StringUtils.strip(line).length()>0){
				// ignore blank lines
				break;				
			}
		}

		reader.close();
		
		if (this.headerIsCommented) {
			ret.remove(ret.size()-1);
		}
		return StringUtils.join("\n", ret);
	}

	
	protected void readHeader()  throws IOException {
		Pair<InputStream, FileChannel> tup = openFile();
		
		StringLineReader reader = new StringLineReader(tup.one);
		Iterator<String> it = reader.iterator();
		
		String line = null;
		String last = null;
		
		while (it.hasNext()) {
			last = line;
			line = it.next();				
			if (line.charAt(0) != meta) {
				break;
			}
		}
		
		reader.close();

		if (this.headerIsCommented) {
			if (last == null) {
				throw new IOException("Input file is missing a header (commented).");
			}
			headerNames = StringUtils.lstrip(last.substring(1)).split("\t");
		} else {
			if (line == null) {
				throw new IOException("Input file is missing a header.");
			}
			headerNames = line.split("\t");
		}
	}
	
	public int findColumnByName(String name) throws IOException {
		if (!this.hasHeader) {
			throw new IOException("Input file doesn't have a header.");
		}
		
		if (headerNames == null) {
			readHeader();
		}
		
		for (int i=0; i<headerNames.length; i++) {
			if (headerNames[i].equals(name)) {
				return i;
			}
		}
		
		return -1;
	}
	

    public Iterator<String[]> lines() throws IOException {
        return new Iterator<String[]>() {
    		Pair<InputStream, FileChannel> tup = openFile();
    		StringLineReader reader = new StringLineReader(tup.one);
    		Iterator<String> it = reader.iterator();

            String[] next = null;
            boolean inHeader = true;
            
            @Override
            public boolean hasNext() {
                if (next == null) {
                    try {
						populate();
					} catch (IOException e) {
					}
                }
                return next != null;
            }

            private void populate() throws IOException {
            	this.next = null;
            	while (this.next == null && it.hasNext()) {
            		String line = it.next();
            		
            		if (meta > 0 && line.charAt(0) == meta) {
            			continue;
            		}
            		
            		if (hasHeader && !headerIsCommented && inHeader) {
            			inHeader = false;
            			continue;
            		}
            		
            		String[] spl = line.split("\t", -1);
            		if (spl.length > 0) { // skip blank lines
            			this.next = spl;
            		}
            	}
            	
            	if (this.next == null) {
            		reader.close();
            	}
            }

            @Override
            public String[] next() {
                if (next == null) {
                    try {
						populate();
					} catch (IOException e) {
					}
                }

                String[] ret = next;
                try {
					populate();
				} catch (IOException e) {
				}
                return ret;
            }
            
        };
    }
    
    
    public char getMeta() {
        return meta;
    }
	public String getFilename() {
		return filename;
	}
	public boolean isHasHeader() {
		return hasHeader;
	}
	public boolean isHeaderIsCommented() {
		return headerIsCommented;
	}
	public String[] getHeaderNames() {
		return headerNames;
	}
}
