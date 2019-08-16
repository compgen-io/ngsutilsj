package io.compgen.ngsutils.tabix;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

import io.compgen.common.io.DataIO;

public class BGZFile {

    public class BGZFileCache {
        protected Map<BGZFileOffset, byte[]> blockCache = new HashMap<BGZFileOffset, byte[]>();
        protected Deque<BGZFileOffset> lru = new LinkedList<BGZFileOffset>();
        protected long size = 0;
        protected long maxSize = 0;
        
        public BGZFileCache() {
            this(16 * 1024 * 1024);  // 16 MB cache
        }
        
        public BGZFileCache(long maxSize) {
            this.maxSize = maxSize;
        }
        
        public byte[] get(long cOffsetBegin, int uOffsetBegin, long cOffsetEnd, int uOffsetEnd) {
            BGZFileOffset key = new BGZFileOffset(cOffsetBegin, uOffsetBegin, cOffsetEnd, uOffsetEnd);

            if (!blockCache.containsKey(key)) {
                return null;
            }
            
            lru.remove(key);
            lru.add(key);
            
            return blockCache.get(key);
        }
        
        public void put(long cOffsetBegin, int uOffsetBegin, long cOffsetEnd, int uOffsetEnd, byte[] payload) {

            while (payload.length + this.size > this.maxSize) {
                removeItem();
            }
            
            BGZFileOffset key = new BGZFileOffset(cOffsetBegin, uOffsetBegin, cOffsetEnd, uOffsetEnd);
            blockCache.put(key, payload);
            lru.add(key);
            this.size += payload.length;
            
        }

        private void removeItem() {
            if (lru.size() > 0) {
                BGZFileOffset key = lru.pop();
                byte[] payload = blockCache.remove(key);
                this.size = this.size - payload.length;
//                System.err.println("Removed: " + key + " from cache");
            }
        }
    }
    
    public class BGZFileOffset {
        @Override
        public String toString() {
            return "BGZFileOffset [cOffsetBegin=" + cOffsetBegin + ", uOffsetBegin="
                    + uOffsetBegin + ", cOffsetEnd=" + cOffsetEnd + ", uOffsetEnd=" + uOffsetEnd
                    + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + (int) (cOffsetBegin ^ (cOffsetBegin >>> 32));
            result = prime * result + (int) (cOffsetEnd ^ (cOffsetEnd >>> 32));
            result = prime * result + uOffsetBegin;
            result = prime * result + uOffsetEnd;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            BGZFileOffset other = (BGZFileOffset) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (cOffsetBegin != other.cOffsetBegin)
                return false;
            if (cOffsetEnd != other.cOffsetEnd)
                return false;
            if (uOffsetBegin != other.uOffsetBegin)
                return false;
            if (uOffsetEnd != other.uOffsetEnd)
                return false;
            return true;
        }

        public final long cOffsetBegin;
        public final int uOffsetBegin;
        public final long cOffsetEnd;
        public final int uOffsetEnd;
        
        public BGZFileOffset(long cOffsetBegin, int uOffsetBegin, long cOffsetEnd, int uOffsetEnd) {
            this.cOffsetBegin = cOffsetBegin;
            this.uOffsetBegin = uOffsetBegin;
            this.cOffsetEnd = cOffsetEnd;
            this.uOffsetEnd = uOffsetEnd;
        }

        private BGZFile getOuterType() {
            return BGZFile.this;
        }

    }
    
//	protected String filename;
	protected RandomAccessFile file;
	protected BGZFileCache cache;
	
    public BGZFile(String filename) throws IOException {
        if (!isBGZFile(filename)) {
            throw new IOException("File: "+filename+" is not a valid BGZ file!");
        }
//        this.filename = filename;
        this.file = new RandomAccessFile(filename, "r");
        this.cache = new BGZFileCache();
    }

    public BGZFile(RandomAccessFile raf) throws IOException {
        if (!isBGZFile(raf)) {
            throw new IOException("RandomAccessFile is not a valid BGZ file!");
        }
//        this.filename = filename;
        this.file = raf;
        this.cache = new BGZFileCache();
    }

    
    
    public FileChannel getChannel() { 
        return file.getChannel();
    }
    
	public void close() throws IOException {
		file.close();
	}

	
	public byte[] readBlocks(long cOffsetBegin,int uOffsetBegin,long cOffsetEnd, int uOffsetEnd) throws IOException, DataFormatException {
	    byte[] buf = cache.get(cOffsetBegin, uOffsetBegin, cOffsetEnd, uOffsetEnd);
	    if (buf != null) {
//	        System.err.println("offset => " + cOffsetBegin + ","+uOffsetBegin + " => "+ cOffsetEnd + ","+uOffsetEnd + " ** CACHED **");
            return buf;
	    }
//	    System.err.println("offset => " + cOffsetBegin + ","+uOffsetBegin + " => "+ cOffsetEnd + ","+uOffsetEnd + " ** FRESH **");
        
	    file.seek(cOffsetBegin);

//		int blockNum = 1;
		
		buf = new byte[0];
		int pos = 0;
		
		while (file.getFilePointer() <= cOffsetEnd) {
            long curOffset = file.getFilePointer();
			byte[] cur = readCurrentBlock();

//			System.err.println("block["+(blockNum++)+"] "+curOffset+", "+cur.length);
			
			int start = 0;
			int end = cur.length;
			
			if (curOffset == cOffsetBegin) {
//				System.err.println("Offsetting chunk (begin); "+uOffsetBegin);
				start = uOffsetBegin;
			}

			if (curOffset >= cOffsetEnd) {
//				System.err.println("Offsetting chunk (end); "+uOffsetEnd);
				end = uOffsetEnd;
			}

			if (start != 0 || end != cur.length) {
			    cur = Arrays.copyOfRange(cur, start, end);
			}

			buf = Arrays.copyOf(buf, buf.length + cur.length);
			for (int i=0; i<cur.length;i++) {
			    buf[pos + i] = cur[i];
			}
			pos = pos + cur.length;
			
//			System.err.println("start="+start+", end="+end+", buf.length="+buf.length);
		}
		
//        System.err.println(" block (complete)");
		cache.put(cOffsetBegin, uOffsetBegin, cOffsetEnd, uOffsetEnd, buf);
		return buf;
	}

	protected byte[] readCurrentBlock() throws IOException {
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
		
		if (bsize == 0) {
		    throw new IOException("Invalid BGZF chunk (missing BSIZE)!");
		}

		// payload
		//byte[] cdata = DataIO.readRawBytes(file, bsize - xlen - 19);
		file.skipBytes(bsize - xlen - 19);
		// crc
		//long crc = DataIO.readUint32(file);
		file.skipBytes(4);
		
		// Uncompressed size [0, 65536]
		long isize = DataIO.readUint32(file);
//        System.err.println("isize => " + isize);

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
		
        // Now that we know all of the sizes, let's read in the compressed chunk and decompress the 
        // full GZip record (naked inflate on cdata was acting funny...)

		// jump back to the beginning of the record
		// and read it into a byte array
		
		byte[] cBuf = new byte[bsize+1];
		file.seek(curOffset);
		file.readFully(cBuf, 0, cBuf.length);

		GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(cBuf));
		byte[] uBuf = new byte[(int) isize];
		int readPos = 0;
		while (readPos < isize) {
			int c = in.read(uBuf, readPos, uBuf.length - readPos);
			if (c == -1) {
				break;
			}
			readPos += c;
//			System.err.println("================================");
//	        System.err.println("reading chunk -- fname  = " + filename+ ", curOffset = " + curOffset +", clen = "+(bsize+1)+", ulen = "+isize);
//            System.err.println("["+curOffset+"] Read "+c+" bytes from BGZF record // strlen=" + new String(uBuf).length()+", readPos="+readPos);
//            System.err.println(new String(uBuf).substring(0,readPos));
		}

		in.close();
//		System.err.println("read chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
		return uBuf;
	}

//	public void dumpIndex() throws IOException {
//		if (index != null) {
//			index.dump();
//		}
//	}
	
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

	   public static boolean isBGZFile(RandomAccessFile fis) throws IOException {
//	      System.err.println("Checking file: "+filename);

           long offset = fis.getFilePointer();

	        try {
//	            FileInputStream fis = new FileInputStream(filename);
	            int magic1 = DataIO.readByte(fis);
	            int magic2 = DataIO.readByte(fis);
	            
	            if (magic1 != 31) {
                    fis.seek(offset);
	                return false;
	            }
	            if (magic2 != 139) {
                    fis.seek(offset);
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
//	                  System.err.println("magic matches");
	                    fis.seek(offset);
	                    return true;
	                }
	            }

                fis.seek(offset);

	        } catch (IOException e) {
	        }

	        return false;       
	    }

}
