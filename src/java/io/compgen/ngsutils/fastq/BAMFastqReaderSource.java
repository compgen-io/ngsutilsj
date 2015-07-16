package io.compgen.ngsutils.fastq;

import io.compgen.common.io.DataIO;
import io.compgen.common.io.PeekableInputStream;
import io.compgen.ngsutils.bam.BamFastqReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPInputStream;

public class BAMFastqReaderSource implements FastqReaderSource {
    public final byte[] BAM_MAGIC = new byte[] {0x42, 0x41, 0x4d, 0x01};

    @Override
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) {
        return new BamFastqReader(is, channel, name);
    }

    @Override
    public boolean autodetect(PeekableInputStream peek) throws IOException {
        // Note: this is expecting for there to be only one RFC1952 subfield in the GZIP header
        byte[] magic = peek.peek(18);

        // look for uncompressed BAM files
        boolean match = true;
        for (int i=0; i< BAM_MAGIC.length; i++) {
            if (magic[i] != BAM_MAGIC[i]) {
                match = false;
                break;
            }
        }
        if (match) {
            return true;
        }
            
        // look for compressed BAM files
        
        if (magic[0] == 0x1f && magic[1] == (byte)0x8b) { // need to cast 0x8b because it is a neg. num in 2-complement
            // GZip magic
            // File is either compressed BGZF or GZIP.
            
            if (magic[3] == 0x04) {
                //mtime
//                peek.peek(4);
    
                // xfl
//                peek.peek(1);
                // OS
//                peek.peek(1);
                
                //xlen
                // peek.peek(2);
                
                byte si1 = magic[12]; //peek.peek();
                byte si2 = magic[13]; //peek.peek();
                int slen = DataIO.bytesUint16(new byte[] { magic[14], magic[15]}); //peek.peek(2));
    
                if (si1 == 66 && si2 == 67 && slen == 2) {
                    // this is BGZF at least - is it a BAM file?
                    // read in the first block and try to in-flate the cdata block to find the BAM magic header (uncompressed)
                    int xlen = DataIO.bytesUint16(new byte[] { magic[10], magic[11]});
                    int bsize = DataIO.bytesUint16(new byte[] { magic[16], magic[17]}); //peek.peek(2));
                    int cDataSize = bsize - xlen - 19;  

                    byte[] blockdata = new byte[bsize+1];
                    int pos = 0;
                    for (int i=0; i<magic.length; i++) {
                        blockdata[pos + i] = magic[i];
                    }
                    pos = magic.length;
                    
//                    System.err.println("Peeking cdata: " + cDataSize + " +8");
                    byte[] cdata = peek.peek(cDataSize + 8); // cDataSize + 4 bytes for CRC + 4 bytes for ISIZE
                    for (int i=0; i<cdata.length; i++) {
                        blockdata[pos + i] = cdata[i];
                    }
                    
//                    byte[] crc32 = new byte[] { cdata[cDataSize], cdata[cDataSize+1], cdata[cDataSize+2], cdata[cDataSize+3] }; 
//                    long isize = DataIO.bytesUint32(new byte[] { cdata[cDataSize+4], cdata[cDataSize+5], cdata[cDataSize+6], cdata[cDataSize+7] });

//                    System.err.println("header    " +StringUtils.byteArrayToString(magic));
//                    System.err.println("xlen      "+xlen);
//                    System.err.println("bsize     "+bsize);
//                    System.err.println("cDataSize "+cDataSize);
//                    System.err.println("cdata     " +StringUtils.byteArrayToString(cdata, 0, 16)+"...");
//                    System.err.println("crc32     "+StringUtils.byteArrayToString(crc32));
//                    System.err.println("isize     "+isize);
//
//                    System.err.println("block     " +StringUtils.byteArrayToString(blockdata, 0, 32)+"...");

                   
                    // It's difficult to work with the Deflater/Inflater classes directly on the cdata block, 
                    // so let's just treat the first block as a self-contained GZIP file, backed by a byte array.
                    
                    byte[] bamMagic = new byte[BAM_MAGIC.length];
                    ByteArrayInputStream bais = new ByteArrayInputStream(blockdata);
                    GZIPInputStream gzis = new GZIPInputStream(bais);
                    gzis.read(bamMagic);
                    gzis.close();
                    
//                    System.err.println("bamMagic  " +StringUtils.byteArrayToString(bamMagic));

                    match = true;
                    for (int i=0; i< BAM_MAGIC.length; i++) {
                        if (bamMagic[i] != BAM_MAGIC[i]) {
                            match = false;
                            break;
                        }
                    }

                    if (match) {
                        // YES! This is a BGZF compressed BAM file!
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int getPriority() {
        return 50;
    }

}
