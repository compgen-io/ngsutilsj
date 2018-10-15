package io.compgen.ngsutils.fastq;

import java.io.IOException;


public interface FastqReader extends Iterable<FastqRead> {
    public void close() throws IOException;
    public boolean hasWarning();
}
