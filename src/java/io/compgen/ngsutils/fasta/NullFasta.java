package io.compgen.ngsutils.fasta;

import java.io.IOException;
import java.util.Iterator;

public class NullFasta extends FastaReader {

    @Override
    public String fetchSequence(String ref, int start, int end) throws IOException {
        String out = "";
        for (int i=start; i < end; i++) {
            out += "N";
        }
        return out;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public Iterator<FastaRecord> iterator() {
        return new Iterator<FastaRecord>() {

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public FastaRecord next() {
                return null;
            }

            @Override
            public void remove() {
            }};
    }

	@Override
	public Iterator<FastaChunkRecord> iteratorChunk(int size) throws IOException {
        return new Iterator<FastaChunkRecord>() {

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public FastaChunkRecord next() {
                return null;
            }

            @Override
            public void remove() {
            }};
	}

}
