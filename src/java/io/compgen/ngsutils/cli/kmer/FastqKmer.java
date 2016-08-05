package io.compgen.ngsutils.cli.kmer;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.kmer.KmerCounter;
import io.compgen.ngsutils.support.SeqUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

@Command(name="kmer-fastq", desc="Splits FASTQ reads into k-mers and counts them", category="kmer", experimental=true)
public class FastqKmer extends AbstractOutputCommand {
    
    private String[] filenames =  null;

    private int size = 25;
    private int bufferSize = 200000;
    private boolean stranded = false;
    private boolean revcomp = false;
    private boolean wildcard = false;

    // TODO: Write output stats to a file (# reads, # unique kmers, # removed due to wildcards)
    private String statsFilename = null;
    
	private File tmpdir = null;

	private List<File> tempFiles = new ArrayList<File>();
	
	// We will write results out asynchronously, but allow only one
	// output thread at a time. Also wait to submit a new job until
	// the old one is done - why? Memory. Only store two full KmerCount
	// objects: (1) reading and (1) writing
	
	private ExecutorService threadPool = Executors.newSingleThreadExecutor();
	private Future<Void> futureVal = null;

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
	public void setFilenames(String[] filenames) {
	    this.filenames = filenames;
	}

    @Option(desc="Count k-mers in a stranded manner (default: count kmer/revcomp together)", name="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
    }

    @Option(desc="Reverse compliment input reads (implies --stranded)", name="revcomp")
    public void setRevcomp(boolean revcomp) {
        this.revcomp = revcomp;
        this.stranded = revcomp;
    }

    @Option(desc="Allow wildcard bases (N)", name="wildcard")
    public void setWildcard(boolean wildcard) {
        this.wildcard = wildcard;
    }

	@Option(desc="Write temporary files to this directory", name="tmp")
	public void setTmpdir(String tmpdir) {
		this.tmpdir = new File(tmpdir);
	}

    @Exec
	public void exec() throws IOException, CommandArgumentException, InterruptedException {
        if (filenames == null) {
            throw new CommandArgumentException("You must supply at least one FASTQ file to count.");
        }
        long totalCount = 0;
        long readCount = 0;
		
        for (String filename: filenames) {
            System.err.println("Counting file: "+filename);

    	    FastqReader reader = Fastq.open(filename);
    	    KmerCounter counter = new KmerCounter(size, stranded, wildcard);
    		
    		for (FastqRead read : reader) {
    		    if (readCount > bufferSize) {
                    tempFiles.add(writeTemp(counter));
                    counter = new KmerCounter(size, stranded, wildcard);
                    readCount = 0;
    		    }
    		    
    		    if (revcomp) {
                    counter.addRead(SeqUtils.revcomp(read.getSeq()));
    		    } else {
                    counter.addRead(read.getSeq());
    		    }
    		    
                readCount++;
                totalCount++;
    		}
    		reader.close();
            tempFiles.add(writeTemp(counter));
        }

        if (verbose) {
            System.err.println("Total reads: "+totalCount);
            System.err.println("Total number of subfiles: "+tempFiles.size());
            System.err.println("Waiting for temporary files to be written...");
        }

        threadPool.shutdown();
        
        while (!threadPool.isTerminated()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        
        if (verbose) {
            System.err.println("Merging subfiles...");
        }
        
        while (tempFiles.size() > KmerMerge.MAX_FILES) {
            File temp = newTempFile();
            temp.deleteOnExit();
            OutputStream os = new GZIPOutputStream(new FileOutputStream(temp));
            KmerMerge.mergeFiles(os, tempFiles.subList(0, KmerMerge.MAX_FILES));

            for (File f : tempFiles.subList(0, KmerMerge.MAX_FILES)) {
                f.delete();
            }
            
            tempFiles = tempFiles.subList(KmerMerge.MAX_FILES, tempFiles.size());
            tempFiles.add(temp);
        }
        
        KmerMerge.mergeFiles(out, tempFiles);

	}

    private File newTempFile() throws IOException {
        File temp;
        if (tmpdir == null) {
            temp = Files.createTempFile(".fastq-kmer-", ".tmp").toFile();
        } else {
            temp = Files.createTempFile(tmpdir.toPath(), ".fastq-kmer-", ".tmp").toFile();
        }
        temp.setReadable(true, true);
        temp.setWritable(true, true);
        temp.setExecutable(false, false);
        temp.deleteOnExit();
        return temp;
    }
    
	private File writeTemp(final KmerCounter counter) throws IOException, InterruptedException {
        if (futureVal != null) {
            while (!futureVal.isDone()) {
                System.err.println("\n\nWaiting for prior write to finish...\n");
                Thread.sleep(1000);
            }
        }

        final File temp = newTempFile();
		System.err.println("\n\n"+temp.getAbsolutePath()+"\n");

		final OutputStream tmpOut = new GZIPOutputStream(new FileOutputStream(temp));
		futureVal = threadPool.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                counter.write(tmpOut, false);
                tmpOut.flush();
                tmpOut.close();
                counter.clear();
                return null;
            }});
		
		return temp;	
	}
}
