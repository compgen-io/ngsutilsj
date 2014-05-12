package org.ngsutils.support;

import java.io.IOException;
import java.io.InputStream;

public class StringLineReader extends AbstractLineReader<String> {
    public StringLineReader(InputStream is) {
        super(is);
    }

    public StringLineReader(String filename) throws IOException {
        super(filename);
    }

    protected String convertLine(String line) {
        return line;
    }
}
