package org.ngsutils.fastq;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;


public class FastqTextReader implements FastqReader {
	final private BufferedReader in;
	private FastqRead nextRead = null;

    protected FastqTextReader(String filename) throws IOException {
        if (filename.endsWith(".gz")) {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                    new FileInputStream(filename))));
        } else if (filename.endsWith(".bz") || filename.endsWith(".bz2")) {
                in = new BufferedReader(new InputStreamReader(new BZip2CompressorInputStream(
                        new FileInputStream(filename), false)));
        } else if (filename.equals("-")) {
            in = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(System.in)));
        } else {
            in = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(new FileInputStream(filename))));
        }
    }

    protected FastqTextReader(InputStream is) throws IOException {
        in = new BufferedReader(new InputStreamReader(is));
    }

	protected FastqTextReader(File file) throws IOException {
	    this(file.getName());
	}
	
	@Override
	public Iterator<FastqRead> iterator() {
		nextRead = FastqRead.read(in);
		return new Iterator<FastqRead>() {
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
	}

   public void close() throws IOException {
        in.close();
    }
}