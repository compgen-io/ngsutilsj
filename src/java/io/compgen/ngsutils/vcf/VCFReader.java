package io.compgen.ngsutils.vcf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.tabix.BGZFile;
import io.compgen.ngsutils.tabix.BGZInputStream;
import io.compgen.ngsutils.tabix.TabixFile;

public class VCFReader {
	protected BufferedReader in;
	protected VCFHeader header=null;
	protected TabixFile tabix=null;
	private boolean closed = false;
    private FileChannel channel = null;
    private String filename = null;
	
    protected boolean removeID = false;
    protected Set<String> removeFilter = null;
    protected Set<String> removeFormat = null;
    protected Set<String> removeInfo = null;
    protected Set<String> removeSample = null;
    protected Set<String> keepFilter = null;
    protected Set<String> keepFormat = null;
    protected Set<String> keepInfo = null;
    protected Set<String> keepSample = null;
    
	public VCFReader(String filename) throws IOException, VCFParseException {
        if (filename.equals("-")) {
            this.filename = "<stdin>";
            in = new BufferedReader(new InputStreamReader(System.in));
        } else if (BGZFile.isBGZFile(filename)) {
            this.filename = filename;
            BGZFile bgzf = new BGZFile(filename);
            channel = bgzf.getChannel();
            in = new BufferedReader(new InputStreamReader(new BGZInputStream(bgzf)));               
            if (TabixFile.isTabixFile(filename)) {
                this.tabix = new TabixFile(bgzf);
            }
        } else if (isGZipFile(filename)){
            this.filename = filename;
            FileInputStream fis = new FileInputStream(filename);
            channel = fis.getChannel();
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(fis)));
        } else {
            this.filename = filename;
            FileInputStream fis = new FileInputStream(filename);
            channel = fis.getChannel();
            in = new BufferedReader(new InputStreamReader(fis));
        }

	}
	

    public String getFilename() {
        return this.filename;
    }
    public FileChannel getChannel() { 
	    return channel;
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
//		readHeader();
	}

	private void readHeader() throws IOException, VCFParseException {
		String fileformat=null;
		List<String> lines = new ArrayList<String>();
		String headerLine = null;
		while (headerLine == null) {
			String line = in.readLine();
			if (line == null || line.equals("")) {
				throw new IOException("Bad VCF header? Missing header line?");
			}
			if (line.startsWith("##fileformat=")) {
				fileformat = line;
			} else if (line.startsWith("##")) {
				lines.add(line);
			} else if (line.startsWith("#CHROM\t")) {
				headerLine = line;
			}
		}
		
		header = new VCFHeader(fileformat, lines, headerLine, removeFilter, removeInfo, removeFormat, removeSample, keepFilter, keepInfo, keepFormat, keepSample);
	}

	public VCFHeader getHeader() throws IOException, VCFParseException {
       if (header == null) {
            readHeader();
        }

		return header;
		
	}
	
	public void close() throws IOException {
		if (!closed) {			
			closed = true;
			in.close();
		}
	}
	
	public Iterator<VCFRecord> iterator() throws IOException, VCFParseException {
	    if (header == null) {
	        readHeader();
	    }
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
					
					next = VCFRecord.parseLine(line, removeID, header);
					
					
				} catch (IOException | VCFParseException e) {
					next = null;
					e.printStackTrace(System.err);
				}
			}
		};
	}

	/**
	 * 
	 * @param ref
	 * @param start zero-based!
	 * @param end
	 * @return
	 * @throws IOException
	 * @throws VCFParseException
	 * @throws DataFormatException 
	 */
	public Iterator<VCFRecord> query(String ref, int start, int end) throws IOException, VCFParseException, DataFormatException {
		if (tabix == null) {
			throw new IOException("Tabix indexed VCF required");
		}

		if (header == null) {
	        readHeader();
	    }

		Iterator<String> it = tabix.query(ref, start, end);
		return new Iterator<VCFRecord> () {

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public VCFRecord next() {
				try {
					return VCFRecord.parseLine(it.next(), false, header);
				} catch (VCFParseException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

    public Iterator<VCFRecord> query(GenomeSpan span) throws IOException, DataFormatException, VCFParseException {
    	return query(span.ref, span.start, span.end);
    }

    public boolean isIndexed(){
		return this.tabix != null;
	}

    public String[] getIndexedSeqNames() {
    	if (this.tabix!=null) {
    		return this.tabix.getSeqNames();
    	}
    	return null;
    }
    
    /**
     * 
     * @param ref
     * @param pos -- 1-based (for a VCF)
     * @param refBase
     * @param alt
     * @return
     * @throws IOException
     * @throws DataFormatException
     * @throws VCFParseException
     */
    public VCFRecord getVariant(String chrom, int pos, String ref, String alt) throws IOException, DataFormatException, VCFParseException {
    	Iterator<VCFRecord> it = query(chrom, pos-1, pos);
    	while (it.hasNext()) {
    		VCFRecord rec = it.next();
    		if (!rec.chrom.equals(chrom)) {
    			continue;
    		}
    		if (rec.pos != pos) {
    			continue;
    		}
    		if (!rec.ref.equals(ref)) {
    			continue;
    		}
    		for (String recAlt: rec.alt) {
    			if (recAlt.equals(alt)) {
    				return rec;
    			}
    		}    		
    	}
    	return null;
    } 


    public void setRemoveID(boolean removeID) {
        this.removeID = removeID;
    }


    public void addRemoveInfo(Set<String> removeInfo) throws VCFParseException {
        if (header == null) {
            if (this.removeInfo == null) {
                this.removeInfo = new HashSet<String>();
            }
            this.removeInfo.addAll(removeInfo);
        } else {
            throw new VCFParseException("You can't remove info after the VCF header has been read.");
        }
    }

    public void addRemoveFormat(Set<String> removeFormat) throws VCFParseException {
        if (header == null) {
            if (this.removeFormat == null) {
                this.removeFormat = new HashSet<String>();
            }
            this.removeFormat.addAll(removeFormat);
        } else {
            throw new VCFParseException("You can't remove formats after the VCF header has been read.");
        }
    }

    public void addRemoveFilter(Set<String> removeFilter) throws VCFParseException {
        if (header == null) {
            if (this.removeFilter == null) {
                this.removeFilter = new HashSet<String>();
            }
            this.removeFilter.addAll(removeFilter);
        } else {
            throw new VCFParseException("You can't remove filters after the VCF header has been read.");
        }
    }


	public void addRemoveSample(Set<String> removeSample) throws VCFParseException {
        if (header == null) {
            if (this.removeSample == null) {
                this.removeSample = new HashSet<String>();
            }
            this.removeSample.addAll(removeSample);
        } else {
            throw new VCFParseException("You can't remove samples after the VCF header has been read.");
        }
	}
    public void addKeepInfo(Set<String> keepInfo) throws VCFParseException {
        if (header == null) {
            if (this.keepInfo == null) {
                this.keepInfo = new HashSet<String>();
            }
            this.keepInfo.addAll(keepInfo);
        } else {
            throw new VCFParseException("You can't remove info after the VCF header has been read.");
        }
    }

    public void addKeepFormat(Set<String> keepFormat) throws VCFParseException {
        if (header == null) {
            if (this.keepFormat == null) {
                this.keepFormat = new HashSet<String>();
            }
            this.keepFormat.addAll(keepFormat);
        } else {
            throw new VCFParseException("You can't remove formats after the VCF header has been read.");
        }
    }

    public void addKeepFilter(Set<String> keepFilter) throws VCFParseException {
        if (header == null) {
            if (this.keepFilter == null) {
                this.keepFilter = new HashSet<String>();
            }
            this.keepFilter.addAll(keepFilter);
        } else {
            throw new VCFParseException("You can't remove filters after the VCF header has been read.");
        }
    }


	public void addKeepSample(Set<String> keepSample) throws VCFParseException {
        if (header == null) {
            if (this.keepSample == null) {
                this.keepSample = new HashSet<String>();
            }
            this.keepSample.addAll(keepSample);
        } else {
            throw new VCFParseException("You can't remove samples after the VCF header has been read.");
        }
	}

	
}
