package io.compgen.ngsutils.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import io.compgen.common.io.PeekableInputStream;

public class BZipFastqReaderSource implements FastqReaderSource {

    @Override
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) throws IOException {
      return new FastqTextReader(new BZip2CompressorInputStream(is), channel, name);
    }

    @Override
    public boolean autodetect(PeekableInputStream peek) throws IOException {
        byte[] magic = peek.peek(3);
        return Arrays.equals(magic, new byte[] {0x42, 0x5A, 0x68});
    }

    @Override
    public int getPriority() {
        return 100;
    }

}
