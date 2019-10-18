package io.compgen.ngsutils.tabix.annotate;

import java.io.IOException;

/**
 * Interface used to annotate tab-delimited (tabix indexed) files
 * 
 * @author mbreese
 *
 */
public interface TabAnnotator {
    /**
     * column name
     * @return
     */
    public String getName();
    
    /**
     * 
     * @param chrom
     * @param start -- always zero-based!
     * @param end -- if not available, -1
     * @param qCols 
     * @return
     * @throws  
     * @throws IOException 
     */
    public String getValue(String chrom, int start, int end, String[] qCols) throws IOException;

    public void close() throws IOException;
}
