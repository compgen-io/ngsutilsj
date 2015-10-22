package io.compgen.ngsutils.fastq;

import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;

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
		nextRead = nextRead();
		
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
                nextRead = nextRead();
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
	
    protected FastqRead nextRead() {
        try {
            String name = in.readLine();
            if (name == null) {
                return null;
            }
            name = name.substring(1); // strip the @
            String comment = null;
            if (name.indexOf(' ') > -1) {
                comment = name.substring(name.indexOf(' ') + 1);
                name = name.substring(0, name.indexOf(' '));
            }
            
            String seq = in.readLine();
            if (seq == null) {
                return null;
            }

            String plus = in.readLine();
            if (plus == null) {
                return null;
            }

            // The seq block may be wrapped (it rarely is, but it's possible)
            
            while (plus.charAt(0) != '+') {
                seq += plus;
                plus = in.readLine();
                if (plus == null) {
                    return null;
                }
            }
            
            String qual = in.readLine();
            if (qual == null) {
                return null;
            }

            // The qual block must be the same length as the seq
            while (qual.length() < seq.length())
            {
                String buf = in.readLine();
                if (buf == null) {
                    return null;
                }
                qual += buf;
            }   
            return new FastqRead(name, seq, qual, comment);
        } catch (Exception e) {
            return null;
        }
    }   

   public void close() throws IOException {
        in.close();
    }
}
