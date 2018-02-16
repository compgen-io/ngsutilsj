package io.compgen.ngsutils.vcf.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

import io.compgen.common.io.DataIO;
import io.compgen.ngsutils.vcf.support.CSIFile.Chunk;

public class BGZFile {
	protected String filename;
	protected CSIFile index;
	protected RandomAccessFile file;
	
	public BGZFile(String filename) throws IOException {
		this.filename = filename;
		File indexFile = new File(filename+".csi");
		if (indexFile.exists()) {
			this.index = new CSIFile(indexFile);
		} else {
			this.index = null;
		}
		this.file = new RandomAccessFile(filename, "r");
//		System.err.println("Opened file: "+filename+", pos="+file.getFilePointer());
	}
	
	public void close() throws IOException {
		file.close();
		if (index != null) {
			index.close();
		}
	}

	public InputStream queryInputStream(String ref, int start) throws IOException, DataFormatException {
		return queryInputStream(ref, start, start+1);
	}
	public InputStream queryInputStream(String ref, int start, int end) throws IOException, DataFormatException {
		String s = query(ref, start, end);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(s.getBytes());
		byte[] buffer = baos.toByteArray();
		return new ByteArrayInputStream(buffer);
	}

	public String query(String ref, int start) throws IOException, DataFormatException {
		return query(ref, start, start+1);
	}
	
	/**
	 * 
	 * @param ref
	 * @param start - zero-based
	 * @param end
	 * @return
	 * @throws IOException
	 * @throws DataFormatException
	 */
	public String query(String ref, int start, int end) throws IOException, DataFormatException {
		String ret = "";
		if (index == null) {
			throw new IOException("Missing CSI index file! Expected: "+filename+".csi");
		}
		for (Chunk chunk: index.find(ref, start, end)) {
			String s = readChunks(chunk.coffsetBegin, chunk.uoffsetBegin, chunk.coffsetEnd, chunk.uoffsetEnd);
			for (String line: s.split("\n")) {
				if (line.startsWith(""+index.meta)) {
					continue;
				}
				String[] cols = line.split("\t");
//				System.out.println(StringUtils.join(",", cols));
				if (cols[index.colSeq-1].equals(ref)) {
					if (index.colEnd > 0) {
						int b = Integer.parseInt(cols[index.colBegin-1]);
						int e = Integer.parseInt(cols[index.colEnd-1]);
						
						if ((index.format&0x10000) == 0) {
							// convert one-based begin coord
							b--;
						}
//						System.out.println("b="+b+", e="+e+", start="+start+", end="+end);
						
						if (b >= start && e < end) {
//							System.out.println("YES!");
							ret += line+"\n";
						}
					} else {
						int b = Integer.parseInt(cols[index.colBegin-1]);

						if ((index.format&0x10000) == 0) {
							// convert one-based begin coord
							b--;
						}
						
//						System.out.println("b="+b+", start="+start+", end="+end);
						if (b >= start && b < end) {
//							System.out.println("YES!!");
							ret += line+"\n";
						}
					}
				}
			}
		}
		
		return ret.equals("") ? null: ret;
	}

	
	protected String readChunks(int cOffsetBegin,int uOffsetBegin,int cOffsetEnd, int uOffsetEnd) throws IOException, DataFormatException {
		// TODO: Load chunks from a cache...
		
//		System.err.println("offset => " + cOffsetBegin + ","+uOffsetBegin + " => "+ cOffsetEnd + ","+uOffsetEnd);
		
		file.seek(cOffsetBegin);
		String s = "";
		
		long curOffset = cOffsetBegin;
		
		while (file.getFilePointer() <= cOffsetEnd) {
			String tmpStr = readCurrentChunk();
			int start = 0;
			int end = tmpStr.length();
			
//			System.err.println("curOffset="+curOffset);
			if (curOffset == cOffsetBegin) {
//				System.err.println("Offsetting chunk (begin); "+uOffsetBegin);
				start = uOffsetBegin;
			}

			curOffset = file.getFilePointer();
//			System.err.println("newOffset="+curOffset);

			if (curOffset > cOffsetEnd) {
//				System.err.println("Offsetting chunk (end); "+uOffsetEnd);
				end = uOffsetEnd;
			}

//			System.err.println("tmpStr.length="+tmpStr.length());
//			System.err.println("start="+start+", end="+end);
			
			s+= tmpStr.substring(start, end); 
			
		}
		
		return s;
	}

	protected String readCurrentChunk() throws IOException {
//		System.err.println("reading chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
		
		long curOffset = file.getFilePointer();
		if (curOffset >= file.length()) {
//			System.err.println(filename+" is all done!");
			return null;
		}
		
		int magic1 = DataIO.readByte(file);
		int magic2 = DataIO.readByte(file);
		
		if (magic1 != 31) {
			throw new IOException("Bad Magic byte1");
		}
		if (magic2 != 139) {
			throw new IOException("Bad Magic byte2");
		}

//		int cm = DataIO.readByte(file);
//		int flg = DataIO.readByte(file);
//		long mtime = DataIO.readUint32(file);
//		int xfl = DataIO.readByte(file);
//		int os = DataIO.readByte(file);
//		DataIO.readRawBytes(file, 8);
		file.skipBytes(8);
		int xlen = DataIO.readUint16(file);
		
		byte[] extra = DataIO.readRawBytes(file, xlen);
		ByteArrayInputStream bais = new ByteArrayInputStream(extra);
		
		int s1 = 0;
		int s2 = 0;
		int bsize = 0;
		
		while (s1 != 66 && s2 != 67) {
			s1 = DataIO.readByte(bais);
			s2 = DataIO.readByte(bais);
			int slen = DataIO.readUint16(bais);
			byte[] payload = DataIO.readRawBytes(bais, slen);
			if (s1 == 66 && s2 == 67) {
				bsize = DataIO.bytesUint16(payload);
			}
		}
		bais.close();

//		byte[] cdata = DataIO.readRawBytes(file, bsize - xlen - 19);
//		long crc = DataIO.readUint32(file);
//		DataIO.readRawBytes(file, bsize - xlen - 19);
//		DataIO.readUint32(file);
		file.skipBytes(bsize - xlen - 19);
		file.skipBytes(4);
		long isize = DataIO.readUint32(file);
		
//		System.out.println("magic1: "+ magic1);
//		System.out.println("magic2: "+ magic2);
//		System.out.println("cm    : "+ cm);
//		System.out.println("flg   : "+ flg);
//		System.out.println("mtime : "+ mtime);
//		System.out.println("xfl   : "+ xfl);
//		System.out.println("os    : "+ os);
//		System.out.println("xlen  : "+ xlen);
//		System.out.println("bsize : "+ bsize);
//		System.out.println("cdata : <"+ cdata.length+" bytes>");
//		System.out.println("crc : "+ crc);
//		System.out.println("isize : "+ isize);
		
//		System.out.println("cur-pointer: " + file.getFilePointer());
		
		byte[] buf = new byte[bsize+1];
		file.seek(curOffset);
		file.read(buf, 0, buf.length);

		// Now that we know all of the sizes, let's read in the compressed chunk and decompress the 
		// full GZip record (naked inflate was acting funny...)
		
		GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(buf));
		byte[] outBuf = new byte[(int) isize];
		int read = 0;
		while (read < isize) {
			int c = in.read(outBuf, read, outBuf.length - read);
			if (c == -1) {
				break;
			}
			read += c;
		}

		in.close();
//		System.err.println("read chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
		return new String(outBuf);
	}

	public void dumpIndex() throws IOException {
		if (index != null) {
			index.dump();
		}
	}
	
	public static boolean isBGZFile(String filename) {
//		System.err.println("Checking file: "+filename);

		try {
			FileInputStream fis = new FileInputStream(filename);
			int magic1 = DataIO.readByte(fis);
			int magic2 = DataIO.readByte(fis);
			
			if (magic1 != 31) {
				fis.close();
				return false;
			}
			if (magic2 != 139) {
				fis.close();
				return false;
			}
			
			DataIO.readRawBytes(fis, 8);
			int xlen = DataIO.readUint16(fis);
			
			byte[] extra = DataIO.readRawBytes(fis, xlen);
			ByteArrayInputStream bais = new ByteArrayInputStream(extra);
			
			int s1 = 0;
			int s2 = 0;
			
			while (s1 != 66 && s2 != 67) {
				s1 = DataIO.readByte(bais);
				s2 = DataIO.readByte(bais);
				if (s1 == 66 && s2 == 67) {
//					System.err.println("magic matches");
					fis.close();
					return true;
				}
			}

			fis.close();

		} catch (IOException e) {
		}

		return false;		
	}
}
