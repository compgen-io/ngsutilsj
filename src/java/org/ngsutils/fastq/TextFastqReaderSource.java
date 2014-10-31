package org.ngsutils.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import org.ngsutils.support.io.PeekableInputStream;

public class TextFastqReaderSource implements FastqReaderSource {

    @Override
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) throws IOException {
      return new FastqTextReader(is, channel, name);
    }

    @Override
    public boolean autodetect(PeekableInputStream peek) throws IOException {
        byte[] magic = peek.peek(1);
        return (magic[0] == 0x40); 
    }

    @Override
    public int getPriority() {
        return 1000;
    }

}
