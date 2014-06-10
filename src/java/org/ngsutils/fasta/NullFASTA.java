package org.ngsutils.fasta;

import java.io.IOException;

public class NullFASTA implements FASTAReader {

    @Override
    public String fetch(String ref, int start, int end) throws IOException {
        String out = "";
        for (int i=start; i < end; i++) {
            out += "N";
        }
        return out;
    }

    @Override
    public void close() throws IOException {
    }

}
