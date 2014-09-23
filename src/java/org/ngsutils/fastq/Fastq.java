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
import org.ngsutils.sqz.SQZ;
import org.ngsutils.sqz.SQZReader;
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
        byte[] magic = peek.peak(4);
        
        if (Arrays.equals(magic, SQZ.MAGIC)) {
            try {
                return SQZReader.open(peek, false, password);
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        } else if (magic[0] == 0x1f && magic[1] == 0x8b) {
            // GZip magic
            return new FastqTextReader(new GZIPInputStream(peek));
        } else if (magic[0] == 0x42 && magic[1] == 0x5A && magic[2] == 0x68) {
            // BZip2 magic
            return new FastqTextReader(new BZip2CompressorInputStream(peek));
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
