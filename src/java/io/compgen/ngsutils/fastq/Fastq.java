package io.compgen.ngsutils.fastq;

import io.compgen.common.io.PeekableInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class Fastq {
    
    private static List<FastqReaderSource> sources = new ArrayList<FastqReaderSource>();
    
    public static void registerSource(Class<? extends FastqReaderSource> clazz) {
        try {
            FastqReaderSource newsrc = clazz.newInstance();
            int i=0;
            for (FastqReaderSource source: sources) {
                if (newsrc.getPriority() < source.getPriority()) {
                    break;
                }
                i++;
            }
            sources.add(i, newsrc);
        } catch (InstantiationException | IllegalAccessException e) {
        }
    }
    
    static {
        registerSource(BAMFastqReaderSource.class);
        registerSource(BZipFastqReaderSource.class);
        registerSource(GZipFastqReaderSource.class);
        registerSource(TextFastqReaderSource.class);
        
            try {
                @SuppressWarnings("unchecked")
                Class<? extends FastqReaderSource> clazz = (Class<? extends FastqReaderSource>) Fastq.class.getClassLoader().loadClass("io.compgen.ngsutils.sqz.SQZFastqReaderSource");
                registerSource(clazz);
            } catch (ClassNotFoundException e) {
            }
    }
    
    public static FastqReader open(String filename) throws IOException {
        return open(filename, null);
    }

    public static FastqReader open(String filename, String password) throws IOException {
        if (filename == null || filename.equals("-")) {
            return open(System.in, password, null, "<stdin>");
        }
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);
        return open(fis, password, fis.getChannel(), file.getName());
    }

    public static FastqReader open(InputStream is, String password, FileChannel channel, String name) throws IOException {
        PeekableInputStream peek = new PeekableInputStream(is);
        
        for (FastqReaderSource source: sources) {
            peek.resetPeek();
            if (source.autodetect(peek)) {
                return source.open(peek, password, channel, name);
            }
        }
        peek.close();
        throw new IOException("Unknown FASTQ input source!");

//        if (Arrays.equals(magic, SQZ.MAGIC)) {
//            try {
//                return SQZReader.open(peek, false, password, false, channel, name);
//            } catch (GeneralSecurityException e) {
//                throw new IOException(e);
//            }
//        } else if (magic[0] == 0x1f && magic[1] == (byte)0x8b) { // need to cast 0x8b because it is a neg. num in 2-complement
//            // GZip magic
//            // File is either compressed BAM or GZIP.
//            
//            if (magic[3] == 0x04) {
//                //mtime
//                peek.peek(4);
//
//                // xfl
//                peek.peek(1);
//                // OS
//                peek.peek(1);
//                
//                //xlen
//                peek.peek(2);
//                
//                byte si1 = peek.peek();
//                byte si2 = peek.peek();
//                int slen = DataIO.bytesUint16(peek.peek(2));
//
//                if (si1 == 66 && si2 == 67 && slen == 2) {
//                    // compressed BAM file
//                    return new BamFastqReader(peek, channel, name);
//                }
//            }
//            return new FastqTextReader(new GZIPInputStream(peek), channel, name);
//        } else if (magic[0] == 0x42 && magic[1] == 0x5A && magic[2] == 0x68) {
//            // BZip2 magic
//            return new FastqTextReader(new BZip2CompressorInputStream(peek), channel, name);
//        } else if (Arrays.equals(magic, new byte[] {0x42, 0x41, 0x4d, 0x01})) {
//            // Uncompressed BAM
//            return new BamFastqReader(peek, channel, name);
//        } else if (magic[0] == 0x40) {
//            // Starts with an '@', so should be FASTQ text
//            return new FastqTextReader(peek, channel, name);
//        } else {
//            // Unknown source...
//            peek.close();
//            throw new IOException("Unknown FASTQ input source!");
//        }
    }
}
