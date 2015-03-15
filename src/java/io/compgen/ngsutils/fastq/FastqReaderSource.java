package io.compgen.ngsutils.fastq;

import io.compgen.ngsutils.support.io.PeekableInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public interface FastqReaderSource {
    public FastqReader open(InputStream is, String password, FileChannel channel, String name) throws IOException;
    // don't read more than 8K (or PeekableInputStream internal buffer size)
    public boolean autodetect(PeekableInputStream peek) throws IOException;
    public int getPriority();
}
