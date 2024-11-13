package io.compgen.ngsutils.tabix;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import io.compgen.common.io.DataIO;

public class BGZWriter {
	private String filename;
	public String getFilename() {
		return filename;
	}

	private int uncompressedMaxBlock = 65280;

	private OutputStream os;
	private byte[] curBuffer = new byte[uncompressedMaxBlock];
	private int curpos = 0;


	public BGZWriter(String filename) throws IOException {
		super();
		this.filename = filename;
		this.os = new FileOutputStream(filename);
	}
	
	public void writeString(String line) throws IOException {
		write(line.getBytes(Charset.defaultCharset()), true);
	}
	public void writeBytes(byte[] bytes) throws IOException {
		write(bytes, false);
	}

	public void write(byte[] bytes, boolean keepTogether) throws IOException {
		if (keepTogether && curpos + bytes.length >= this.uncompressedMaxBlock) {
			flush();
		}
		for (int i=0; i<bytes.length; i++) {
			write(bytes[i]);
		}
	}

	public void write(byte b) throws IOException {
		curBuffer[curpos++] = b;
		if (curpos >= this.uncompressedMaxBlock) {
			flush();
		}
	}


	public void close() throws IOException {
		flush();
		
		// The last block is empty and has a fixed 28 bytes
		int[] last = new int[] {0x1f,0x8b,0x08,0x04,0x00,0x00,0x00,0x00,0x00,0xff,0x06,0x00,0x42,0x43,0x02,0x00,0x1b,0x00,0x03,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
		for (int i: last) {
			DataIO.writeRawByte(os, (byte) (i & 0xFF));
		}
		os.flush();
		os.close();
	}
	
	private void flush() throws IOException {
//		System.err.println("Flushing (curpos="+curpos+") ");
		if (curpos == 0 ) {
			return;
		}
//		byte[] bufferBytes = curBuffer.getBytes(Charset.defaultCharset());
		int isize = curpos;
		
		Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		CRC32 crc = new CRC32();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();		
		DeflaterOutputStream dos = new DeflaterOutputStream(baos, def);
		dos.write(curBuffer, 0, curpos);
		dos.close();

		crc.reset();
		crc.update(curBuffer, 0, curpos);
				
		byte[] cdata = baos.toByteArray();
		long crcVal = crc.getValue();

		DataIO.writeRawByte(os, (byte) (31 & 0xFF)); // write magic bytes
		DataIO.writeRawByte(os, (byte) (139 & 0xFF));
		DataIO.writeRawByte(os, (byte) (8 & 0xFF)); // compression-method (DEFLATE)
		DataIO.writeRawByte(os, (byte) (4 & 0xFF)); // flags = 4 => EXTRA follows
		DataIO.writeUint32(os, 0); // write mtime (uint32)
		DataIO.writeRawByte(os, (byte) (0 & 0xFF)); // xfl (none)
		DataIO.writeRawByte(os, (byte) (255 & 0xFF)); // OS (unknown)
		DataIO.writeUint16(os, 6); // EXTRA length = 6 (hard coded in bgzip!)
	
		// Hard coded EXTRA fields (from in bgzip again)
		DataIO.writeRawByte(os, (byte) (66 & 0xFF));
		DataIO.writeRawByte(os, (byte) (67 & 0xFF));
		DataIO.writeUint16(os, 2); 
		DataIO.writeUint16(os, cdata.length + 6 + 19); // bsize is cdata + xlen + rest of gzip header

		DataIO.writeRawBytes(os, cdata); // write compressed bytes
		DataIO.writeUint32(os, crcVal); // write crc32
		DataIO.writeUint32(os, isize); // uncompressed size
		os.flush();

		curpos = 0;
	}
}
