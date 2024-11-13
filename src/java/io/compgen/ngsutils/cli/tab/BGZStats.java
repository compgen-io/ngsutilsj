package io.compgen.ngsutils.cli.tab;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.io.DataIO;
import io.compgen.ngsutils.tabix.BGZFile;

@Command(name = "bgzip-stats", desc = "Extract the headers of a bgzip file", category = "help", hidden = true)
public class BGZStats extends AbstractOutputCommand {
    private String infile;

    @UnnamedArg(name = "infile", required = true)
    public void setFilename(String fname) throws CommandArgumentException {
        infile = fname;
    }

    @Exec
    public void exec() throws Exception {
    	if (!BGZFile.isBGZFile(infile, verbose)) {
    		throw new IOException("Not a valid BGZ file!");
    	}
    	RandomAccessFile file = new RandomAccessFile(infile, "r");
		long curOffset = file.getFilePointer();

		while (curOffset >= file.length()) {
			readCurrentBlock(file);
			curOffset = file.getFilePointer();
		}

    	
    }


	public void readCurrentBlock(RandomAccessFile file) throws IOException {		
		int magic1 = DataIO.readByte(file);
		int magic2 = DataIO.readByte(file);
		
		if (magic1 != 31) {
			throw new IOException("Bad Magic byte1");
		}
		if (magic2 != 139) {
			throw new IOException("Bad Magic byte2");
		}

		int cm = DataIO.readByte(file);
		int flg = DataIO.readByte(file);
		long mtime = DataIO.readUint32(file);
		int xfl = DataIO.readByte(file);
		int os = DataIO.readByte(file);

//		file.skipBytes(8);
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
		
		if (bsize == 0) {
		    throw new IOException("Invalid BGZF chunk (missing BSIZE)!");
		}

		// payload
		byte[] cdata = DataIO.readRawBytes(file, bsize - xlen - 19);
//		file.skipBytes(bsize - xlen - 19);
		// crc
		long crc = DataIO.readUint32(file);
		file.skipBytes(4);
		
		// Uncompressed size [0, 65536]
		long isize = DataIO.readUint32(file);
//        System.err.println("isize => " + isize);

		System.out.println("magic1: "+ magic1);
		System.out.println("magic2: "+ magic2);
		System.out.println("cm    : "+ cm);
		System.out.println("flg   : "+ flg);
		System.out.println("mtime : "+ mtime);
		System.out.println("xfl   : "+ xfl);
		System.out.println("os    : "+ os);
		System.out.println("xlen  : "+ xlen);
		System.out.println("bsize : "+ bsize);
		System.out.println("cdata : <"+ cdata.length+" bytes>");
		System.out.println("crc : "+ crc);
		System.out.println("isize : "+ isize);
		
        // Now that we know all of the sizes, let's read in the compressed chunk and decompress the 
        // full GZip record (naked inflate on cdata was acting funny...)

		// jump back to the beginning of the record
		// and read it into a byte array
//		
//		byte[] cBuf = new byte[bsize+1];
//		file.seek(curOffset);
//		file.readFully(cBuf, 0, cBuf.length);
//
//		GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(cBuf));
//		byte[] uBuf = new byte[(int) isize];
//		int readPos = 0;
//		while (readPos < isize) {
//			int c = in.read(uBuf, readPos, uBuf.length - readPos);
//			if (c == -1) {
//				break;
//			}
//			readPos += c;
////			System.err.println("================================");
////	        System.err.println("reading chunk -- fname  = " + filename+ ", curOffset = " + curOffset +", clen = "+(bsize+1)+", ulen = "+isize);
////            System.err.println("["+curOffset+"] Read "+c+" bytes from BGZF record // strlen=" + new String(uBuf).length()+", readPos="+readPos);
////            System.err.println(new String(uBuf).substring(0,readPos));
//		}
//
//		in.close();
////		System.err.println("read chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
//		b = new BGZBlock(curOffset,bsize+1,uBuf);
//		cache.put(b);
//		return b;
	}


}
