package io.compgen.ngsutils.cli.kmer;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.FastaRecord;
import io.compgen.ngsutils.kmer.KmerCounter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

@Command(name="kmer-fasta", desc="Splits a FASTA sequence into k-mers and counts them", category="kmer", experimental=true)
public class FastaKmer extends AbstractOutputCommand {
    
    private String[] filenames =  null;

    private int size = 25;
    private int bufferSize = 200000;
    private boolean stranded = false;
    private boolean wildcard = false;

	private File tmpdir = null;

	private List<File> tempFiles = new ArrayList<File>();
	private ExecutorService threadPool = Executors.newSingleThreadExecutor();

	public FastaKmer(){
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

    @Option(desc="Count k-mers in a stranded manner (default: count kmer/revcomp together)", name="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
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
	public void exec() throws IOException, CommandArgumentException {
        if (filenames == null) {
            throw new CommandArgumentException("You must supply at least one FASTQ file to count.");
        }
        long totalCount = 0;
        long readCount = 0;
		
        for (String filename: filenames) {
            System.err.println("Counting file: "+filename);

    	    FastaReader reader = FastaReader.open(filename);
    	    KmerCounter counter = new KmerCounter(size, stranded, wildcard);
    		
    		for (FastaRecord read : IterUtils.wrap(reader.iterator())) {
    		    if (readCount > bufferSize) {
                    tempFiles.add(writeTemp(counter));
                    counter = new KmerCounter(size, stranded, wildcard);
                    readCount = 0;
    		    }
    		    
                counter.addRead(read.seq);
    		    
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

        if (verbose) {
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
            OutputStream os = new FileOutputStream(temp);
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
    
	private File writeTemp(final KmerCounter counter) throws IOException {
	    final File temp = newTempFile();
		System.err.println("\n\n"+temp.getAbsolutePath()+"\n");

		final OutputStream tmpOut = new GZIPOutputStream(new FileOutputStream(temp));

		threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    counter.write(tmpOut, false);
                    tmpOut.flush();
                    tmpOut.close();
                    counter.clear();
                } catch (IOException e) {
                }
            }});
		
		return temp;
		
	}
}
