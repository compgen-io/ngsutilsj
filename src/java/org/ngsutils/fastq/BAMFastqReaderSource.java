package org.ngsutils.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import org.ngsutils.bam.BamFastqReader;
import org.ngsutils.support.io.DataIO;
import org.ngsutils.support.io.PeekableInputStream;

public class BAMFastqReaderSource implements FastqReaderSource {
    public final byte[] BAM_MAGIC = new byte[] {0x42, 0x41, 0x4d, 0x01};

    @Override
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) {
        return new BamFastqReader(is, channel, name);
    }

    @Override
    public boolean autodetect(PeekableInputStream peek) throws IOException {
        byte[] magic = peek.peek(16);

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
            // File is either compressed BAM or GZIP.
            
            if (magic[3] == 0x04) {
                //mtime
//                peek.peek(4);
    
                // xfl
//                peek.peek(1);
                // OS
//                peek.peek(1);
                
                //xlen
//                peek.peek(2);
                
                byte si1 = magic[12]; //peek.peek();
                byte si2 = magic[13]; //peek.peek();
                int slen = DataIO.bytesUint16(new byte[] { magic[14], magic[15]}); //peek.peek(2));
    
                if (si1 == 66 && si2 == 67 && slen == 2) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getPriority() {
        return 20;
    }

}
