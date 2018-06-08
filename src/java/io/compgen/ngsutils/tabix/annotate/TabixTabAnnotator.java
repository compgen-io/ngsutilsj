package io.compgen.ngsutils.tabix.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixFile;

public class TabixTabAnnotator implements TabAnnotator {

    private String name;
    private TabixFile tabix;
    private int col;
    private boolean collapse;
    
    public TabixTabAnnotator(String name, String fname, int col, boolean collapse) throws IOException {
        this.name = name;
        this.tabix = new TabixFile(fname);
        this.col = col;
        this.collapse = collapse;
    }
    public TabixTabAnnotator(String name, String fname) throws IOException {
        this(name, fname, -1, false);
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getValue(String ref, int start, int end) throws IOException {
        List<String> matches = new ArrayList<String>();
        try{
            for (String s: IterUtils.wrap(tabix.query(ref, start, end))) {
                String[] cols = StringUtils.rstrip(s).split("\t");
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
        
        if (collapse) {
            return StringUtils.join(",", StringUtils.unique(matches));
        }
        
        return StringUtils.join(",", matches);
    }

    public void close() throws IOException {
        tabix.close();
    }
    
}
