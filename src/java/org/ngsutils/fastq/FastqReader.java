package org.ngsutils.fastq;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;


public class FastqReader implements Iterable<FastqRead> {
	final private String filename;
	final private BufferedReader in;
	private FastqRead nextRead = null;

	public FastqReader(String filename) throws IOException {
		this.filename = filename;
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

	public FastqReader(File file) throws IOException {
		filename = file.getName();
		if (filename.endsWith(".gz")) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(
					new FileInputStream(file))));
		} else {
			in = new BufferedReader(new InputStreamReader(
					new BufferedInputStream(new FileInputStream(file))));
		}
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

	public String getFilename() {
		return filename;
	}

    public void close() throws IOException {
        in.close();
    }
}