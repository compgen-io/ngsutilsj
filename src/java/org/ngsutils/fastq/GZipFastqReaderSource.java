package org.ngsutils.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.ngsutils.support.io.PeekableInputStream;

public class GZipFastqReaderSource implements FastqReaderSource {

    @Override
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) throws IOException {
      return new FastqTextReader(new GzipCompressorInputStream(is), channel, name);
    }

    @Override
    public boolean autodetect(PeekableInputStream peek) throws IOException {
        byte[] magic = peek.peek(2);
        return Arrays.equals(magic, new byte[] {0x1f, (byte) 0x8B});// need to cast 0x8b because it is a neg. num in 2-complement
    }

    @Override
    public int getPriority() {
        return 150;
    }

}
