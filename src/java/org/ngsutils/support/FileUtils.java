package org.ngsutils.support;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

public class FileUtils {
    public static Iterable<String> lineReader(final String filename) throws IOException {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator(){
                    try {
                        return new Iterator<String>() {
                            BufferedReader br = new BufferedReader(new FileReader(filename));
                            String next = br.readLine();
                            @Override
                            public boolean hasNext() {
                                return next == null;
                            }

                            @Override
                            public String next() {
                                String line = next;
                                try {
                                    next = br.readLine();
                                    if (next == null) {
                                        br.close();
                                    }
                                } catch (IOException e) {
                                    next = null;
                                }
                                return line;
                            }

                            @Override
                            public void remove() {
                            }};
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
            }};
    }
}
