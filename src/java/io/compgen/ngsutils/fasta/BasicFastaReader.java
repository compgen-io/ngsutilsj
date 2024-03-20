package io.compgen.ngsutils.fasta;

import java.io.IOException;
import java.util.Iterator;

import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

public class BasicFastaReader extends FastaReader {
    protected final String filename;
    private boolean closed = false;
    private StringLineReader reader = null;
    
    protected BasicFastaReader(String filename) {
        this.filename = filename;
    }

    @Override
    /*
     * This is a *really* bad idea for a genome-sized file. Best to use the IndexedFastaFile version!
     */
    public String fetchSequence(String ref, int start, int end) throws IOException {
        if (closed) {
            throw new IOException("FastaReader closed");
        }
        Iterator<FastaRecord> it = iterator();
        String seq = null;
        for (FastaRecord record: IterUtils.wrap(it)) {
            if (record.name.equals(ref)) {
                seq = record.seq.replaceAll("[ \\t\\r\\n]", "").substring(start, end);
                break;
            }
        }
        
        if (reader != null) {
            reader.close();
        }
        
        return seq;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public Iterator<FastaRecord> iterator() throws IOException {
        if (closed) {
            throw new IOException("FastaReader closed");
        }
        
        reader = new StringLineReader(filename);
        
        return new Iterator<FastaRecord> () {

        Iterator<String> it = null;
        
        FastaRecord nextRecord = null;
        String nextNameLine = null;
        
        private void readNext() {
            if (!it.hasNext()) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
                nextRecord = null;
                return;
            }
            
            while (nextNameLine == null) {
                if (!it.hasNext()) {
                    nextRecord = null;
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                    return;
                }
                String line = it.next();
                if (line.charAt(0) == '>') {
                    nextNameLine = line;
                }
            }
            
            String[] header = nextNameLine.split(" ", 2);
            String name = header[0].substring(1);
            String comment = null;
            if (header.length>1) {
                comment = header[1];
            }
            String seq = "";
            nextNameLine = null;

            while (nextNameLine == null) {
                if (!it.hasNext()) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                    break;
                }
                String line = it.next();
                if (line == null || line.equals("") || line.trim().length() == 0) {
                    continue;
                }
                if (line.charAt(0) == '>') {
                    nextNameLine = line;
                } else {
                    seq += StringUtils.strip(line);
                }
            }
            
            nextRecord = new FastaRecord(name, seq, comment);
        }

        @Override
        public boolean hasNext() {
            if (it == null) {
                it = reader.iterator();
                readNext();
            }
            return (nextRecord != null);
        }

                @Override
        public FastaRecord next() {
            if (it == null) {
                it = reader.iterator();
                readNext();
            }
            FastaRecord cur = nextRecord;
            readNext();
            return cur;
        }

        @Override
        public void remove() {
        }

        };
    }

    

    public Iterator<FastaChunkRecord> iteratorChunk(final int size) throws IOException {
        if (closed) {
            throw new IOException("FastaReader closed");
        }
                
        reader = new StringLineReader(filename);
        
        return new Iterator<FastaChunkRecord> () {
        	String currentName = null;
        	String currentComment = null;
        	int pos = -1;
        	String buffer = "";

        	FastaChunkRecord next = null;
            Iterator<String> it = null;

			@Override
			public boolean hasNext() {
				if (next == null && pos == -1) {
					populate();
				}
				return next != null;
			}

			private void populate() {
				if (next != null) {
					return;
				}
				if (buffer.length() >= size) {
					String subseq = buffer.substring(0, size);
					next = new FastaChunkRecord(currentName, subseq, pos, currentComment);
					pos += size;
					buffer = buffer.substring(size);
					return;
				}
				
				if (it == null) {
					it = reader.iterator();
				}
				
				if (!it.hasNext()) {
					return;
				}
				
				String line = it.next();
				if (line.startsWith(">")) {
					if (buffer.length() > 0) {
						next = new FastaChunkRecord(currentName, buffer, pos, currentComment);
					}

					String[] spl = line.substring(1).split(" ", 2);
					currentName = spl[0];
					if (spl.length > 1) {
						currentComment = spl[1];
					} else {
						currentComment = null;
					}
					buffer = "";
					pos = 0;
										
				} else {
					buffer += StringUtils.strip(line);
				}
				
				if (buffer.length() < size) {
					populate(); // keep calling this until we have enough in the buffer... 
            					// If we already have a "next", then this is immediately returned.
				}
				
				if (next == null && buffer.length() >= size) {
					String subseq = buffer.substring(0, size);
					next = new FastaChunkRecord(currentName, subseq, pos, currentComment);
					pos += size;
					buffer = buffer.substring(size);
				}
			}

			@Override
			public FastaChunkRecord next() {
				FastaChunkRecord ret = next;
				next = null;
				populate();
				
				return ret;
			}

        

        };
    }
    
}
