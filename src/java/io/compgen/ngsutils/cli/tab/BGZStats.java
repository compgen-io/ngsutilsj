package io.compgen.ngsutils.cli.tab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

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
    	System.out.println("Examining file: "+infile);
    	System.out.println("===========================");
    	if (!BGZFile.isBGZFile(infile, verbose)) {
    		throw new IOException("Not a valid BGZ file!");
    	}
    	RandomAccessFile file = new RandomAccessFile(infile, "r");
		long curOffset = file.getFilePointer();

		while (curOffset < file.length()) {
			readCurrentBlock(file);
	    	System.out.println("===========================");
			curOffset = file.getFilePointer();
		}
		
		file.close();    	
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

		System.out.println("magic1: "+ magic1);
		System.out.println("magic2: "+ magic2);
		System.out.println("cm    : "+ cm);
		System.out.println("flg   : "+ flg);
		System.out.println("mtime : "+ mtime);
		System.out.println("xfl   : "+ xfl);
		System.out.println("os    : "+ os);
		System.out.println("xlen  : "+ xlen);

		while (s1 != 66 && s2 != 67) {
			s1 = DataIO.readByte(bais);
			s2 = DataIO.readByte(bais);
			int slen = DataIO.readUint16(bais);

			System.out.println("  s1    : "+ s1);
			System.out.println("  s2    : "+ s2);
			System.out.println("  slen  : "+ slen);


			byte[] payload = DataIO.readRawBytes(bais, slen);
			if (s1 == 66 && s2 == 67) {
				bsize = DataIO.bytesUint16(payload);
				System.out.println("   bsize: "+ bsize);
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
//		file.skipBytes(4);
		
		// Uncompressed size [0, 65536]
		long isize = DataIO.readUint32(file);
//        System.err.println("isize => " + isize);

		System.out.println("bsize : "+ bsize);
		System.out.println("cdata : <"+ cdata.length+" bytes>");
		System.out.println("crc   : "+ crc);
		System.out.println("isize : "+ isize);
		
		byte[] uBuf = new byte[(int) isize];
		ByteArrayInputStream bis = new ByteArrayInputStream(cdata);
		InflaterInputStream iis = new InflaterInputStream(bis, new Inflater(true));
		int readPos = 0;
		while (readPos < isize) {
			int c = iis.read(uBuf, readPos, uBuf.length - readPos);
			if (c == -1) {
				break;
			}
			readPos += c;
		}
		iis.close();

        // Now that we know all of the sizes, let's read in the compressed chunk and decompress the 
        // full GZip record (naked inflate on cdata was acting funny...)

		// jump back to the beginning of the record
		// and read it into a byte array
		

//		
////		
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
//
//			//			System.err.println("================================");
////	        System.err.println("reading chunk -- fname  = " + filename+ ", curOffset = " + curOffset +", clen = "+(bsize+1)+", ulen = "+isize);
////            System.err.println("["+curOffset+"] Read "+c+" bytes from BGZF record // strlen=" + new String(uBuf).length()+", readPos="+readPos);
////            System.err.println(new String(uBuf).substring(0,readPos));
//		}
//		in.close();

		String tmp = new String(uBuf);
		if (tmp.length() > 50) {
			System.out.println(" >> " + tmp.substring(0, 50));
			System.out.println(" >> ..");
			System.out.println(" >> " + tmp.substring(tmp.length()-50));
		} else {
			System.out.println(" >> " + tmp);
		}
		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
		
		dos.write(uBuf);
		dos.close();
		
		byte[] cdata2 = baos.toByteArray();
		CRC32 crc2 = new CRC32();
		crc2.reset();
		crc2.update(uBuf);
		long crcVal = crc2.getValue();

		System.out.println("DEFLATED: <"+cdata2.length+" bytes>");
		System.out.println("CRC32   : "+crcVal);
		
		System.out.print("EXPECTED: ");
		for (int i=0; i<40 && i < cdata.length; i++) {
			System.out.print(String.format("%02x", cdata[i]));
			System.out.print(" ");
		}
		System.out.println(""); 
		System.out.print("GOT     : ");
		for (int i=0; i<40 && i < cdata2.length; i++) {
			System.out.print(String.format("%02x", cdata2[i]));
			System.out.print(" ");
		}
		System.out.println("");
		
		
	}


}
