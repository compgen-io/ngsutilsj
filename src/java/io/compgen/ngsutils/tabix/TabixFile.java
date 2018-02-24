package io.compgen.ngsutils.tabix;

import java.io.File;
import java.io.IOException;
import java.util.zip.DataFormatException;

import io.compgen.common.StringUtils;
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
	public String query(String ref, int start) throws IOException, DataFormatException {
		return query(ref, start, start+1);
	}
	
	/**
	 * Returns lines from the TABIX file that completely CONTAIN the query range
	 * 
     * YES:
     *      |             |
     *      |-------------|
     *      |   [query]   |
     *      [query]
     *              [query]
     * 
     * No:
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

    public String query(String ref, int start, int end) throws IOException, DataFormatException {
		String ret = "";
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


//        System.err.println("Finding chunks for: "+ref+":"+start+","+end);

//        int i=1;
		for (Chunk chunk: index.find(ref, start, end)) {
//	        System.err.println("  chunk["+(i++)+"]"+chunk.coffsetBegin +","+ chunk.uoffsetBegin +" -> "+ chunk.coffsetEnd+","+ chunk.uoffsetEnd);
		    
			byte[] chunkBuf = bgzf.readChunks(chunk.coffsetBegin, chunk.uoffsetBegin, chunk.coffsetEnd, chunk.uoffsetEnd);
			String s = new String(chunkBuf, "UTF-8");
			for (String line: s.split("\n")) {
				if (line.startsWith(""+index.getMeta())) {
					continue;
				}
				String[] cols = line.split("\t");
//				System.err.println(StringUtils.join(",", cols));
				if (cols[index.getColSeq()-1].equals(ref)) {
				    int b = -1, e = -1;
				    try {
    					if (index.getColEnd() > 0) {
    						b = Integer.parseInt(cols[index.getColBegin()-1]);
    						e = Integer.parseInt(cols[index.getColEnd()-1]);
    					} else {
                            b = Integer.parseInt(cols[index.getColBegin()-1]);
                            e = Integer.parseInt(cols[index.getColBegin()-1]);;
    					}
    					
    					if ((index.getFormat()&0x10000) == 0) {
    						// convert one-based begin coord (in bgzip file)
    						b--;
    					}

    					if (b > end) {
    					    // we are past the pos we need, so no more valid lines
    					    break;
    					}
    					
    					// return if the spans overlap at all -- if necessary, the 
    					// calling function can re-parse.
    	
                        if (
                                (b <= start && start < e) || // query start is within range
                                (start <= b && e < end) ||   // b,e is contained completely by query
                                (b < end && end <= e)        // query end is within range
                            ) {
                            ret += line+"\n";
                            
    					}
                    } catch (Exception ex) {
                        System.err.println("ref="+ref+", b="+b+", e="+e+", start="+start+", end="+end);
                        System.err.println("line => " + line);
                        System.err.println("cols => " + StringUtils.join(", ", cols));
                        for (String l: s.split("\n")) {
                            System.err.println("s => " + l);
                        }
                        ex.printStackTrace(System.err);
                        System.exit(1);
                    }
				}
			}
		}
		
		return ret.equals("") ? null: ret;
	}

    public void dumpIndex() throws IOException {
        index.dump();        
    }
}
