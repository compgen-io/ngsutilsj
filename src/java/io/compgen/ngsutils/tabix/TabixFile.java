package io.compgen.ngsutils.tabix;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.DataFormatException;

import io.compgen.ngsutils.support.LogUtils;

public class TabixFile {
	protected String filename;
	protected BGZFile bgzf;
	protected TabixIndex index;
    	
    private boolean checkChr = false;
    private boolean addChr = false;
    private boolean removeChr = false;
        
	public TabixFile(String filename) throws IOException {
		this.filename = filename;
		this.bgzf = new BGZFile(filename);
        this.index = null;
        if (new File(filename+".csi").exists()) {
            this.index = new CSIFile(new File(filename+".csi"));
        } else if (new File(filename+".tbi").exists()) {
            this.index = new TBIFile(new File(filename+".tbi"));
        } else {
            throw new IOException("Missing tabix index (expected *.csi or *.tbi)!");
        }
	}
	
	public void close() throws IOException {
	    bgzf.close();
	}

//	public InputStream queryInputStream(String ref, int start) throws IOException, DataFormatException {
//		return queryInputStream(ref, start, start+1);
//	}
//	public InputStream queryInputStream(String ref, int start, int end) throws IOException, DataFormatException {
//		String s = query(ref, start, end);
//		ByteArrayOutputStream baos = new ByteArrayOutputStream();
//		baos.write(s.getBytes());
//		byte[] buffer = baos.toByteArray();
//		return new ByteArrayInputStream(buffer);
//	}

	public boolean containsSeq(String name) {
	    if (index != null) {
	        return index.containsSeq(name);
	    } 
	    return false;
	}
	
	/**
	 * 
	 * @param ref
	 * @param start (zero-based)
	 * @return
	 * @throws IOException
	 * @throws DataFormatException
	 */
	public Iterator<String> query(String ref, int start) throws IOException, DataFormatException {
		return query(ref, start, start+1);
	}
	
	/**
	 * Returns lines from the TABIX file that overlaps the query range
	 * 
     * YES:
     *      |             |
     *      |-------------|
     *      |   [query]   |
     *      [query]
     *              [query]
     * 
     * Also YES:
     * 
     *      |             |
     *      |-------------|
     *      |             |
     *                 [query]
     * [query]
     *    [ ===== query===== ]
	 * 
	 * 
	 * @param ref
	 * @param start - zero-based
	 * @param end
	 * @return
	 * @throws IOException
	 * @throws DataFormatException
	 */

    public Iterator<String> query(String ref, int start, int end) throws IOException, DataFormatException {
		if (index == null) {
			throw new IOException("Missing TBI or CSI index file!");
		}

        if (!checkChr) {
            checkChr = true;
            if (ref.startsWith("chr") && !containsSeq(ref) && containsSeq(ref.substring(3))) {
                LogUtils.printOnce(System.err, "NOTE: Auto converting between UCSC/Ensembl chrom format: chr* => * ("+filename+")");
                removeChr = true;
            } else if (!ref.startsWith("chr") && !containsSeq(ref) && containsSeq("chr"+ref)) {
                LogUtils.printOnce(System.err, "NOTE: Auto converting between UCSC/Ensembl chrom format: * => chr* ("+filename+")");
                addChr = true;
            }
        }
        
        if (addChr) {
            ref = "chr"+ref;
        }
        if (removeChr) {
            ref = ref.substring(3);
        }

        return new TabixQueryIterator(ref, start, end, index, bgzf);
	}

    public Iterator<String> lines() {
        return new Iterator<String>() {

            byte[] buf = null;
            int pos = -1;
            String next = null;
            
            @Override
            public boolean hasNext() {
                if (pos == -1) {
                    populate();
                }
                return next != null;
            }

            private void populate() {
                if (buf != null) {
                    for (int i=pos; i<buf.length; i++) {
                        if (buf[i] == '\n') {
                            if (buf[i-1] == '\r') {
                                next = new String(buf, pos, i-pos-1);
                            } else {
                                next = new String(buf, pos, i-pos);
                            }
                            pos = i+1;
                            return;
                        }
                    }
                }
                
                
                try {
                    byte[] block = bgzf.readCurrentBlock();
                    if (block == null) {
                        if (buf!=null && pos < buf.length) { 
                            next = new String(buf, pos, buf.length-pos);
                            pos = buf.length;
                        } else {
                            next = null;
                        }
                        return;
                    }
                    
                    if (buf != null) {
                        byte[] newbuf = new byte[(buf.length-pos) + block.length];
                        
                        for (int i=0; i+pos<buf.length; i++) {
                            newbuf[i] = buf[pos+i];
                        }
                        int offset = buf.length-pos;
                        for (int i=0; i<block.length; i++) {
                            newbuf[offset + i]=block[i];
                        }
                        pos = 0;
                        buf = newbuf;
                    } else {
                        pos = 0;
                        buf = block;
                    }
                    populate();
                    
                } catch (IOException e) {
                }
            }

            @Override
            public String next() {
                if (pos == -1) {
                    populate();
                }

                String ret = next;
                populate();
                return ret;
            }
            
        };
    }
    
    
    public char getMeta() {
        return index.getMeta();
    }

    public int getColSeq() {
        return index.getColSeq();
    }

    public int getColBegin() {
        return index.getColBegin();
    }

    public int getColEnd() {
        return index.getColEnd();
    }

    public int getFormat() {
        return index.getFormat();
    }

    public int getSkipLines() {
        return index.getSkipLines();
    }

    public void dumpIndex() {
        try {
            index.dump();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
