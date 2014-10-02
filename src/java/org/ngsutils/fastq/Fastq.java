package org.ngsutils.fastq;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.ngsutils.bam.BamFastqReader;
import org.ngsutils.sqz.SQZ;
import org.ngsutils.sqz.SQZReader;
import org.ngsutils.support.io.DataIO;
import org.ngsutils.support.io.PeekableInputStream;

public class Fastq {
    public static FastqReader open(String filename) throws IOException {
        return open(filename, null);
    }
    public static FastqReader open(File file) throws IOException {
        return open(file, null);
    }
    public static FastqReader open(InputStream is) throws IOException {
        return open(is, null);
    }

    public static FastqReader open(String filename, String password) throws IOException {
        if (filename.equals("-")) {
            return open(System.in, password);
        }
        return open(new File(filename), password);
    }
    public static FastqReader open(File file, String password) throws IOException {
        return open(new BufferedInputStream(new FileInputStream(file)), password);
    }

    public static FastqReader open(InputStream is, String password) throws IOException {
        PeekableInputStream peek = new PeekableInputStream(is);
        byte[] magic = peek.peek(4);
        
        if (Arrays.equals(magic, SQZ.MAGIC)) {
            try {
                return SQZReader.open(peek, false, password);
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        } else if (magic[0] == 0x1f && magic[1] == (byte)0x8b) { // need to cast 0x8b because it is a neg. num in 2-complement
            // GZip magic
            // File is either compressed BAM or GZIP.
            
            if (magic[3] == 0x04) {
                //mtime
                peek.peek(4);

                // xfl
                peek.peek(1);
                // OS
                peek.peek(1);
                
                //xlen
                peek.peek(2);
                
                byte si1 = peek.peek();
                byte si2 = peek.peek();
                int slen = DataIO.bytesUint16(peek.peek(2));

                if (si1 == 66 && si2 == 67 && slen == 2) {
                    // compressed BAM file
                    return new BamFastqReader(peek);
                }
            }
            return new FastqTextReader(new GZIPInputStream(peek));
        } else if (magic[0] == 0x42 && magic[1] == 0x5A && magic[2] == 0x68) {
            // BZip2 magic
            return new FastqTextReader(new BZip2CompressorInputStream(peek));
        } else if (Arrays.equals(magic, new byte[] {0x42, 0x41, 0x4d, 0x01})) {
            // Uncompressed BAM
            return new BamFastqReader(peek);
        } else if (magic[0] == 0x40) {
            // Starts with an '@', so should be FASTQ text
            return new FastqTextReader(peek);
        } else {
            // Unknown source...
            peek.close();
            throw new IOException("Unknown FASTQ input source!");
        }
    }
}
