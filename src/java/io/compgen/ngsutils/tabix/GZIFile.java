package io.compgen.ngsutils.tabix;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.compgen.common.io.DataIO;

public class GZIFile {
	private int numBlocks;
	private long[] compressedOffsets;
	private long[] uncompressedOffsets;
	
	public GZIFile(String filename) throws IOException {
		FileInputStream fis = new FileInputStream(new File(filename));
//		System.err.println("GZI file: " + filename);
		
		long entries = DataIO.readUint64(fis);
		
		if (entries > Integer.MAX_VALUE) {
			throw new IOException("Too many blocks in BGZ file to handle.");
		}
		
		this.numBlocks = (int) entries;
//		System.err.println("GZI num_blocks: " + this.numBlocks);
		
		this.compressedOffsets = new long[this.numBlocks + 1];
		this.uncompressedOffsets = new long[this.numBlocks + 1];
		
		this.compressedOffsets[0] = 0;
		this.uncompressedOffsets[0] = 0;
		
		for (int i=1; i<this.numBlocks; i++) {
			this.compressedOffsets[i] = DataIO.readUint64(fis);
			this.uncompressedOffsets[i] = DataIO.readUint64(fis);
			
//			System.err.println("block " + i + ": " + this.compressedOffsets[i] + " / " + this.uncompressedOffsets[i]);			
		}
	}
	
	
	/**
	 * Return the offset of the compressed block that contains the 
	 * uncompressed position uPos
	 * 
	 * @param uPos
	 * @return
	 */
	public long getCompressedOffset(long uPos) {
		for (int i=1; i<this.numBlocks; i++) {
			if (uncompressedOffsets[i] > uPos) {
				return compressedOffsets[i-1];
			}
		}
		return compressedOffsets[compressedOffsets.length-1];
	}
	
	/**
	 * Return the offset between the given uPos and the start of the
	 * BGZ block's uncompressed data
	 * 
	 * @param uPos
	 * @return the offset to uPos (global pos) within the BGZ block's uncompressed data
	 */
	public int getUncompressedBlockOffset(long uPos) {
		for (int i=1; i<this.numBlocks; i++) {
			if (uncompressedOffsets[i] > uPos) {
				return (int) (uPos - uncompressedOffsets[i-1]);
			}
		}
		return (int) (uPos - uncompressedOffsets[uncompressedOffsets.length-1]);
	}
}
