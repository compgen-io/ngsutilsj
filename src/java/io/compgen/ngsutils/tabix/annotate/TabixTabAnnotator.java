package io.compgen.ngsutils.tabix.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixFile;

public class TabixTabAnnotator implements TabAnnotator {
    private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();

    private String name;
    private TabixFile tabix;
    private int col;
    private boolean collapse;
    private boolean first;
    
    public TabixTabAnnotator(String name, String fname, int col, boolean collapse, boolean first) throws IOException {
        this.name = name;
        this.tabix = getTabixFile(fname);
        this.col = col;
        this.collapse = collapse;
        this.first = first;
    }
    public TabixTabAnnotator(String name, String fname) throws IOException {
        this(name, fname, -1, false, false);
    }

    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getValue(String ref, int start, int end, String[] qCols) throws IOException {
        List<String> matches = new ArrayList<String>();
        try{
            for (String s: IterUtils.wrap(tabix.query(ref, start, end))) {
                String[] cols = s.split("\t", -1);
                if (col > -1 && col < cols.length) {
                    matches.add(cols[col]);
                } else {
                    matches.add(name);
                }
            }
        } catch(DataFormatException e) {
            throw new IOException(e);
        }
        
        if (col == -1) {
            if (matches.size()>0) {
                return name;
            }
        }

        if (first && matches.size() > 1) {
            return matches.get(0);
        }
        if (collapse) {
            return StringUtils.join(",", StringUtils.unique(matches));
        }
        
        
        return StringUtils.join(",", matches);
    }

    public void close() throws IOException {
        tabix.close();
    }
    
}
