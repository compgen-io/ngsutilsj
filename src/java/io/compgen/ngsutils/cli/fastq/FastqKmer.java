package io.compgen.ngsutils.cli.fastq;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.AbstractLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.support.KmerCounter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

@Command(name="fastq-kmer", desc="Splits reads into k-mers and counts them", category="fastq", experimental=true)
public class FastqKmer extends AbstractOutputCommand {
    public class KmerRecord {
        public final String kmer;
        private long count;
        private Set<String> names = new HashSet<String>();

        public KmerRecord(String kmer, long count, String[] names) {
            this.kmer = kmer;
            this.count = count;
            for (String name: names) {
                addName(name);
            };
        }
        
        private void addName(String name) {
            this.names.add(name);
        }

        public KmerRecord(String kmer, long count) {
            this.kmer = kmer;
            this.count = count;
        }
        
        public long getCount() {
            return count;
        }
        
        public Set<String> getReadNames() {
            return Collections.unmodifiableSet(names);
        }
        
        public void merge(KmerRecord other) {
            this.count += other.count;
            this.names.addAll(other.names);
        }
        public void write(OutputStream os) throws IOException {
            String val = kmer + "\t" +count+"\t"+StringUtils.join("\t", names)+"\n";
            os.write(val.getBytes(Charset.forName("UTF-8")));
        }
    }

    public class KmerLineReader extends AbstractLineReader<KmerRecord> {
        public KmerLineReader(String filename) throws IOException {
            super(filename);
        }

        @Override
        protected KmerRecord convertLine(String line) {
            String[] vals = line.split("\t");
            String kmer = vals[0];
            long count = Long.parseLong(vals[1]);
            KmerRecord ret = new KmerRecord(kmer, count);
            
            for (int i=2; i<vals.length; i++) {
                ret.addName(vals[i]);
            }
            
            return ret;
        }
        
    }
    
    private String[] filenames =  null;

    private int size = 25;
    private int bufferSize = 200000;
	private boolean noCompressTemp = false;
	private boolean verbose = false;

	private File tmpdir = null;

	private ArrayList<String> tempFiles = new ArrayList<String>();

	public FastqKmer(){
	}

    @Option(desc="k-mer length", name="kmer", defaultValue="25")
    public void setSize(int size) {
        this.size = size;
    }

    @Option(desc="Number of reads to include in temporary files (default: 200000)", name="buf", defaultValue="200000")
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

	@UnnamedArg(name="FILE1...")
	public void setFilenames(String[] filenames) throws IOException {
	    this.filenames = filenames;
	}

	@Option(desc="Compress temporary files (default: true)", name="nogz")
	public void setNoCompressTemp(boolean noCompressTemp) {
		this.noCompressTemp = noCompressTemp;
	}

	@Option(desc="Write temporary files to this directory", name="tmp")
	public void setTmpdir(String tmpdir) {
		this.tmpdir = new File(tmpdir);
	}

    @Exec
	public void exec() throws IOException, CommandArgumentException {
        if (filenames == null) {
            throw new CommandArgumentException("You must supply at least one FASTQ file to count.");
        }
        long totalCount = 0;
        long readCount = 0;
		
        for (String filename: filenames) {
            System.err.println("Counting file: "+filename);

    	    FastqReader reader = Fastq.open(filename);
    	    KmerCounter counter = new KmerCounter(size);
    		
    		for (FastqRead read : reader) {
    		    if (readCount > bufferSize) {
                    writeTemp(counter);
    		        counter.clear();
    		        readCount = 0;
    		    }
    		    
    		    counter.addRead(read.getName(), read.getSeq());
    		    
                readCount++;
                totalCount++;
    		}
    		reader.close();
            writeTemp(counter);
        }

        if (verbose) {
			System.err.println("Total reads: "+totalCount);
			System.err.println("Total number of subfiles: "+tempFiles.size());
			System.err.println("Merging subfiles...");
		}
        
        List<KmerLineReader> readers = new ArrayList<KmerLineReader>();
        List<Iterator<KmerRecord>> its = new ArrayList<Iterator<KmerRecord>>();
        List<KmerRecord> buffer = new ArrayList<KmerRecord>();
        for (String tmp: tempFiles) {
            KmerLineReader klr = new KmerLineReader(tmp);
            readers.add(klr);
            its.add(klr.iterator());
            buffer.add(null);
        }
        
        boolean done = false;
        long count = 0;
        
        while (!done) {
            String bestKmer = null;
            done = true;
            for (int i=0; i<readers.size(); i++) {
                KmerRecord cur = null;
                if (buffer.get(i) == null) {
                    if (its.get(i).hasNext()) {
                        cur = its.get(i).next();
                        buffer.set(i, cur);
                    }
                }
                
                if (cur != null && (bestKmer == null || buffer.get(i).kmer.compareTo(bestKmer) < 0)) {
                    bestKmer = cur.kmer;
                }
            }

//            System.err.println("merging kmer:"  +bestKmer);
            
            if (bestKmer != null) {
                done = false;
                count ++;
                KmerRecord acc = new KmerRecord(bestKmer, 0);

                for (int i=0; i<buffer.size(); i++) {
                    if (buffer.get(i).kmer.equals(bestKmer)) {
                        acc.merge(buffer.get(i));
                        buffer.set(i, null);
                    }
                }
                
                acc.write(out);
            }
        }
        System.err.println("Total k-mers: "+count);

        for (KmerLineReader r: readers) {
            r.close();            
        }
        
	}

	private void writeTemp(KmerCounter counter) throws IOException {
        String suffix = ".tmp";
        if (!noCompressTemp) {
            suffix = ".gz"; 
        }
        File temp;
		if (tmpdir == null) {
			temp = Files.createTempFile(".fastqSort", suffix).toFile();
		} else {
			temp = Files.createTempFile(tmpdir.toPath(), ".fastqSort", suffix).toFile();
		}
		temp.setReadable(true, true);
		temp.setWritable(true, true);
		temp.setExecutable(false, true);
		tempFiles.add(temp.getAbsolutePath());
		temp.deleteOnExit();

		OutputStream tmpOut;
		if (noCompressTemp) {
			tmpOut = new BufferedOutputStream(new FileOutputStream(temp));
		} else {
			tmpOut = new GZIPOutputStream(new FileOutputStream(temp));
		}

		System.err.println("\n\n"+temp.getAbsolutePath()+"\n");

		counter.write(tmpOut);
		tmpOut.flush();
		tmpOut.close();
	}
}
