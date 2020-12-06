package io.compgen.ngsutils.cli.fasta;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.utils.IOUtils;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-revcomp", desc="Reverse compliment the sequences in a FASTA file", category="fasta", experimental=true)
public class FastaRevcomp extends AbstractOutputCommand {
	
	public interface FASTAStreamReader extends Iterable<String> {
		public void close() throws IOException;
	}
	
	public class FASTAStringReader implements FASTAStreamReader {
		private final String seq;
		public FASTAStringReader(String seq) {
			this.seq = seq;
		}
		
		@Override
		public void close() {
			
		}

		@Override
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				private int i = 0;

				@Override
				public boolean hasNext() {
					return i < 2;
				}

				@Override
				public String next() {
					i++;
					if (i==1) {
						return ">seq";
					} else {
						return seq;
					}
				}};
		}
	}
	
	public class FASTAFileReader implements FASTAStreamReader {
		private final int buflen;
		private final InputStream in;
		
		public FASTAFileReader(String fname, int buflen) throws FileNotFoundException {
			this.buflen = buflen;
			if (fname.equals("-")) {
				this.in = System.in;
			} else {
				this.in = new BufferedInputStream(new FileInputStream(fname));
			}
		}
		
		public FASTAFileReader(String fname) throws FileNotFoundException {
			this(fname, 100);
		}
		
		@Override
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				private String nextVal = null;
				private boolean first = true;
				private boolean done = false;

				@Override
				public boolean hasNext() {
					if (first) {
						nextVal = populate();
					}
					return (nextVal != null);
				}

				private String populate() {
					if (done) {
						return null;
					}
					first = false;
					String s = "";
					
					while ((s.length()>0 && s.charAt(0) == '>') || s.length() < buflen) {
						try {
							byte[] b = new byte[1];
							int read = in.read(b);
							if (read == -1) {
								done = true;
								break;
							}
							if (b[0] == '\r') {
								continue;
							}
							if (b[0] == '\n') {
								break;
							}
							
							s += new String(b);
							
						} catch (IOException e) {
							break;
						}
					}
					
					return s;
				}

				@Override
				public String next() {
					String val = nextVal;
					nextVal = populate();
					return val;
				}
				
			};
		}

		@Override
		public void close() throws IOException {
			if (this.in != System.in) { 
				this.in.close();
			}
		}
	}
	
	
	
    private String filename = null;
    private String seq = null;
//    private int wrap = 60;
    
    private Deque<String> tmpFiles = new ArrayDeque<String>();
    private String buffer = "";
    
    private int bufLen = 16 * 1024 * 1024;
    
    
//    @Option(name="wrap", charName="w", desc="Wrap output sequences", helpValue="len", defaultValue="60")
//    public void setWrap(int wrap) {
//        this.wrap = wrap;
//    }
//
//    @Option(name="nowrap", desc="Remove all wrapping (one sequence per line, regardless of length)")
//    public void setNowrap(boolean nowrap) {
//        this.wrap = -1;
//    }

    @Option(name="seq", charName="s",  desc="Raw sequence to reverse compliment")
    public void setSeq(String seq) {
        this.seq = seq;
    }

    @Option(name = "file", charName="f", desc="FASTA file to reverse compliment")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null && seq == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        FASTAStreamReader reader = null;
        
        if (seq != null) {
        	reader = new FASTAStringReader(seq);
        } else {
        	reader = new FASTAFileReader(filename, bufLen);
        }
        
        for (String line: reader) {
        	if (line.length()>0 && line.charAt(0) == '>') {
    			flushSeq();
        		out.write((line+"\n").getBytes());
        	} else {
        		buffer = buffer + line;
        		
        		if (buffer.length() > bufLen) {
        			writeTempFile();
        		}
        	}
        }
		flushSeq();
    }

    private void flushSeq() throws FileNotFoundException, IOException {
    	if (buffer.length() > 0) {
    		writeTempFile();
    	}
    	while (tmpFiles.size()>0) {
			InputStream tmp = new GZIPInputStream(new FileInputStream(tmpFiles.pollLast()));
			IOUtils.copy(tmp, out);
			tmp.close();
			out.write("\n".getBytes());
    	}
    }

    private void writeTempFile() throws IOException {
		File temp = Files.createTempFile(".fasta-revcomp-", "seq").toFile();
		temp.setReadable(true, true);
		temp.setWritable(true, true);
		temp.setExecutable(false, false);
		tmpFiles.add(temp.getAbsolutePath());
		temp.deleteOnExit();

		OutputStream tmpOut = new GZIPOutputStream(new FileOutputStream(temp));
		tmpOut.write(SeqUtils.revcomp(buffer).getBytes());
		tmpOut.close();
		
		buffer = "";			
    	
    }
    
}
