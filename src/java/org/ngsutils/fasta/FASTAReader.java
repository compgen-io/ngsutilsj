package org.ngsutils.fasta;

import java.io.IOException;

public interface FASTAReader {

    /*
     * start is zero-based
     */
    public abstract String fetch(String ref, int start, int end) throws IOException;

    public abstract void close() throws IOException;

}