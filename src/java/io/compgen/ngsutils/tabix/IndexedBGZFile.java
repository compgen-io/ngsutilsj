package io.compgen.ngsutils.tabix;

import java.io.File;
import java.io.IOException;

import io.compgen.ngsutils.tabix.BGZFile.BGZBlock;

public class IndexedBGZFile {
	private BGZFile bgz = null;
	private GZIFile gzi = null;

	private BGZBlock curBlock = null;
	private int curBlockPos = 0;
	private long curPos = 0;
	
	public IndexedBGZFile(String filename) throws IOException {
		if (!new File(filename+".gzi").exists()) {
	        throw new IOException("Missing GZI index file!");
	    }
	    
	    this.gzi = new GZIFile(filename + ".gzi");        
	    this.bgz = new BGZFile(filename);
	}
	
	public void close() throws IOException {
		bgz.close();
	}
	
	public void seek(long uPos) throws IOException {
		long cOffset = gzi.getCompressedOffset(uPos);
		int uOffset = gzi.getUncompressedBlockOffset(uPos);
		
		
//		System.err.println("Seeking to: " + uPos + " => " + cOffset + ", " + uOffset);
		if (curBlock == null || curBlock.cPos != cOffset) {
//			System.err.println("Reading block at: " + cOffset);
			curBlock = bgz.readBlock(cOffset);
		}
		curBlockPos = uOffset;
		curPos = uPos;
	}

	public byte readByte() throws IOException {
		if (curBlock == null) {
			seek(0);
		}
		
		if (curBlockPos >= curBlock.uBuf.length) {
			seek(curPos);
		}

		byte b = curBlock.uBuf[curBlockPos];
		curBlockPos++;
		curPos++;

		return b;
		
//		return curBlock.uBuf[curBlockPos++];
	}
}
