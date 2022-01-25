package io.compgen.ngsutils.tdf.annotate;

/**
 * Interface used to annotate tab-delimited (NOT tabix indexed) files
 * 
 * @author mbreese
 *
 */

public interface TDFAnnotator {

    /**
     * column name
     * @return
     */
    public String getName();
    
    /**
     * 
     * @param in - the value to lookup
     * @return the new value
     * @throws  
     * @throws IOException 
     */
    public String getValue(String key) throws TDFAnnotationException;

	public void validate() throws TDFAnnotationException;

}
