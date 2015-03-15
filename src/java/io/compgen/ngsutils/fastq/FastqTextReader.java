package io.compgen.ngsutils.fastq;

import io.compgen.ngsutils.support.progress.FileChannelStats;
import io.compgen.ngsutils.support.progress.ProgressMessage;
import io.compgen.ngsutils.support.progress.ProgressUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.NoSuchElementException;


public class FastqTextReader implements FastqReader {
	final private BufferedReader in;
	private FastqRead nextRead = null;
	private FileChannel channel = null;
	private String name = null;

//    protected FastqTextReader(String filename) throws IOException {
//        if (filename.endsWith(".gz")) {
//            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(
//                    new FileInputStream(filename))));
//        } else if (filename.endsWith(".bz") || filename.endsWith(".bz2")) {
//                in = new BufferedReader(new InputStreamReader(new BZip2CompressorInputStream(
//                        new FileInputStream(filename), false)));
//        } else if (filename.equals("-")) {
//            in = new BufferedReader(new InputStreamReader(
//                    new BufferedInputStream(System.in)));
//        } else {
//            in = new BufferedReader(new InputStreamReader(
//                    new BufferedInputStream(new FileInputStream(filename))));
//        }
//    }

    public FastqTextReader(InputStream is, FileChannel channel, String name) throws IOException {
        in = new BufferedReader(new InputStreamReader(is));
        this.channel = channel;
        this.name = name;
    }

//	protected FastqTextReader(File file) throws IOException {
//	    this(file.getName());
//	}
	
	@Override
	public Iterator<FastqRead> iterator() {
		nextRead = FastqRead.read(in);
		
		Iterator<FastqRead> it = new Iterator<FastqRead>() {
            @Override
            public boolean hasNext() {
                return (nextRead != null);
            }

            @Override
            public FastqRead next() {
                if (nextRead == null) {
                    throw new NoSuchElementException();
                }
                FastqRead old = nextRead;
                nextRead = FastqRead.read(in);
                return old;
            }

            @Override
            public void remove() {
                // doesn't do anything...
            }};
		
        if (channel == null) {
            return it;
        }
            
		return ProgressUtils.getIterator((name == null) ? "FASTQ": name, it, new FileChannelStats(channel), new ProgressMessage<FastqRead>() {
                @Override
                public String msg(FastqRead current) {
                    return current.getName();
                }});
	}

   public void close() throws IOException {
        in.close();
    }
}
