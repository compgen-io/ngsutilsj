package io.compgen.ngsutils.pileup;

import java.io.IOException;
import java.util.Iterator;
import java.util.zip.DataFormatException;

import io.compgen.ngsutils.tabix.TabixFile;

public class TabixPileup {
    protected TabixFile tabix;
    public TabixPileup(String filename) throws IOException {
        tabix = new TabixFile(filename);
    }
    
    public Iterator<PileupRecord> query(String ref, int start, int end) throws IOException, DataFormatException {
        final Iterator<String> it = tabix.query(ref, start, end);
        
        return new Iterator<PileupRecord>() {

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public PileupRecord next() {
                String next = it.next();
                if (next != null) {
                    return PileupRecord.parse(next);
                }
                return null;
            }
        };
    }
    
}
