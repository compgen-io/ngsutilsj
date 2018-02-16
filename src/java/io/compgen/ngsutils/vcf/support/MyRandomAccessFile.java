package io.compgen.ngsutils.vcf.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import io.compgen.common.StringUtils;

public class MyRandomAccessFile extends InputStream {
	private RandomAccessFile file;
	private String filename;
	public MyRandomAccessFile(String filename) throws FileNotFoundException {
		System.err.println("ctor: "+filename);
		this.filename = filename;
		this.file = new RandomAccessFile(filename, "r");
	}
	public void close() throws IOException {
		System.err.println("close: "+filename);
		file.close();		
	}
	public long getFilePointer() throws IOException {
		System.err.println("getFilePointer: "+filename+" => "+ file.getFilePointer());
		return file.getFilePointer();
	}
	public void seek(long pos) throws IOException {
		file.seek(pos);
		System.err.println("seek: "+filename+" => "+ pos+" ? " + file.getFilePointer());
	}
	public long length() throws IOException {
		System.err.println("length: "+filename+" => "+ file.length());
		return file.length();
	}
	public int read(byte[] buf, int i, int length) throws IOException {
		int count = file.read(buf, i, length);		
		System.err.println("read(byte[]): "+filename+" => "+count + " :: " +StringUtils.byteArrayToString(buf));
		return count;
	}
	@Override
	public int read() throws IOException {
		System.err.println("read(int): "+filename+" => ");
		return file.read();
	}
}
