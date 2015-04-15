package io.compgen.ngsutils.annotation;

import io.compgen.common.IterUtils;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads annotations from a RepeatMasker output
 * 
 * @author mbreese
 * 
 */

public class BEDAnnotationSource extends AbstractAnnotationSource<BedRecord> {
    public BEDAnnotationSource(String filename) throws FileNotFoundException, IOException {
        loadFile(new FileInputStream(new File(filename)));
    }
    public BEDAnnotationSource(InputStream is) throws FileNotFoundException, IOException {
        loadFile(is);
    }

    protected void loadFile(InputStream is) throws IOException {
        for (BedRecord record: IterUtils.wrap(BedReader.readInputStream(is))) {
            addAnnotation(record.getCoord(), record);
        }
    }
    
    @Override
    public String[] getAnnotationNames() {
        return new String[] { "name", "score" };
    }

}
