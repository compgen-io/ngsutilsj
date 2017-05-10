package io.compgen.ngsutils.fasta;

import java.io.IOException;
import java.util.Iterator;

import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

public class BasicFastaReader extends FastaReader {
    protected final String filename;
    private boolean closed = false;
    private StringLineReader reader = null;
    
    protected BasicFastaReader(String filename) {
        this.filename = filename;
    }

    @Override
    /*
     * This is a *really* bad idea for a genome-sized file. Best to use the IndexedFastaFile version!
     */
    public String fetchSequence(String ref, int start, int end) throws IOException {
        if (closed) {
            throw new IOException("FastaReader closed");
        }
        Iterator<FastaRecord> it = iterator();
        String seq = null;
        for (FastaRecord record: IterUtils.wrap(it)) {
            if (record.name.equals(ref)) {
                seq = record.seq.replaceAll("[ \\t\\r\\n]", "").substring(start, end);
                break;
            }
        }
        
        if (reader != null) {
            reader.close();
        }
        
        return seq;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public Iterator<FastaRecord> iterator() throws IOException {
        if (closed) {
            throw new IOException("FastaReader closed");
        }
        
        reader = new StringLineReader(filename);
        
        return new Iterator<FastaRecord> () {

        Iterator<String> it = null;
        
        FastaRecord nextRecord = null;
        String nextNameLine = null;
        
        private void readNext() {
            if (!it.hasNext()) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
                nextRecord = null;
                return;
            }
            
            while (nextNameLine == null) {
                if (!it.hasNext()) {
                    nextRecord = null;
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                    return;
                }
                String line = it.next();
                if (line.charAt(0) == '>') {
                    nextNameLine = line;
                }
            }
            
            String[] header = nextNameLine.split(" ", 2);
            String name = header[0].substring(1);
            String comment = null;
            if (header.length>1) {
                comment = header[1];
            }
            String seq = "";
            nextNameLine = null;

            while (nextNameLine == null) {
                if (!it.hasNext()) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                    break;
                }
                String line = it.next();
                if (line == null || line.equals("") || line.trim().length() == 0) {
                    continue;
                }
                if (line.charAt(0) == '>') {
                    nextNameLine = line;
                } else {
                    seq += StringUtils.strip(line);
                }
            }
            
            nextRecord = new FastaRecord(name, seq, comment);
        }

        @Override
        public boolean hasNext() {
            if (it == null) {
                it = reader.iterator();
                readNext();
            }
            return (nextRecord != null);
        }

                @Override
        public FastaRecord next() {
            if (it == null) {
                it = reader.iterator();
                readNext();
            }
            FastaRecord cur = nextRecord;
            readNext();
            return cur;
        }

        @Override
        public void remove() {
        }

        };
    }

}
