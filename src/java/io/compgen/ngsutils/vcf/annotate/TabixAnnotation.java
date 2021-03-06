package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class TabixAnnotation extends AbstractBasicAnnotator {
    private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();

    final protected String name;
    final protected String filename;
    final protected TabixFile tabix;
    final protected int colNum;
    final protected int altColNum;
    final protected boolean isNumber;
    final protected boolean collapse;

    final protected String colDefString;
    
    public TabixAnnotation(String name, String filename, int colNum, boolean isNumber, int altColNum, boolean collapse)
            throws IOException {
        this.name = name;
        this.filename = filename;
        this.colNum = colNum;
        this.isNumber = isNumber;
        this.altColNum = altColNum;
        this.tabix = getTabixFile(filename);
        this.collapse = collapse;
        this.colDefString = null;
    }

    public TabixAnnotation(String name, String filename, String colName, boolean isNumber, String altColName, boolean collapse)
            throws IOException {
        this.name = name;
        this.filename = filename;
        this.isNumber = isNumber;
        this.tabix = getTabixFile(filename);
        this.collapse = collapse;
        

        int altColNum = this.tabix.findColumnByName(altColName);
        if (altColNum == -1) {
        	throw new IOException("Unknown column name: "+ altColName);
        }
        this.altColNum = altColNum;

        int colNum = this.tabix.findColumnByName(colName);
        if (colNum == -1) {
        	throw new IOException("Unknown column name: "+ colName);
        }
        this.colNum = colNum;
        this.colDefString = colName;
    }

    public TabixAnnotation(String name, String filename, String colName, boolean isNumber, int altColNum, boolean collapse)
            throws IOException {
        this.name = name;
        this.filename = filename;
        this.isNumber = isNumber;
        this.altColNum = altColNum;
        this.tabix = getTabixFile(filename);
        this.collapse = collapse;
        
        int colNum = this.tabix.findColumnByName(colName);
        if (colNum == -1) {
        	throw new IOException("Unknown column name: "+ colName);
        }
        
        this.colNum = colNum;
        this.colDefString = colName;
    }

    public TabixAnnotation(String name, String filename, int colNum, boolean isNumber, String altColName, boolean collapse)
            throws IOException {
        this.name = name;
        this.filename = filename;
        this.isNumber = isNumber;
        this.tabix = getTabixFile(filename);
        this.collapse = collapse;
        this.colNum = colNum;
        int altColNum = this.tabix.findColumnByName(altColName);
        if (altColNum == -1) {
        	throw new IOException("Unknown column name: "+ altColName);
        }
        this.altColNum = altColNum;
        this.colDefString = null;
    }


    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    @Override
    public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
        try {
            if (colNum == -1) {
                header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in Tabix file",
                        filename, null, null, null));
            } else {
            	if (this.colDefString != null) {
                // use 1-based indexing here...
	                if (isNumber) {
	                    header.addInfo(VCFAnnotationDef.info(name, ".", "Float",
	                            "Column " + VCFHeader.quoteString(colDefString) + " from file", filename, null, null, null));
	                } else {
	                    header.addInfo(VCFAnnotationDef.info(name, ".", "String",
	                            "Column " + VCFHeader.quoteString(colDefString) + " from file", filename, null, null, null));
	                }
            	} else {
	                if (isNumber) {
	                    header.addInfo(VCFAnnotationDef.info(name, ".", "Float",
	                            "Column " + (colNum+1) + " from file", filename, null, null, null));
	                } else {
	                    header.addInfo(VCFAnnotationDef.info(name, ".", "String",
	                            "Column " + (colNum+1) + " from file", filename, null, null, null));
	                }
            	}
            }
        } catch (VCFParseException e) {
            throw new VCFAnnotatorException(e);
        }
    }

    @Override
    public void close() throws VCFAnnotatorException {
        try {
            tabix.close();
        } catch (IOException e) {
            throw new VCFAnnotatorException(e);
        }
    }

    @Override
    public void annotate(VCFRecord record) throws VCFAnnotatorException {
        String chrom;
        int pos;
        int endpos;
        try {
            chrom = getChrom(record);
            pos = getPos(record);
            endpos = getEndPos(record);
        } catch (VCFAnnotatorMissingAltException e) {
            return;
        }
        
        try {
//            System.err.println("Looking for TABIX rows covering: "+record.getChrom() +":"+ record.getPos()+" ("+filename+")");
//            String tabixLines = tabix.query(record.getChrom(), record.getPos() - 1);
//            if (tabixLines == null) {
////                System.err.println("Not found");
//                return;
//            }
//
            List<String> vals = new ArrayList<String>();
            boolean found = false;

            if (altColNum > -1) {
                // need to verify the alt column.
            	
                for (String line : IterUtils.wrap(tabix.query(chrom, pos - 1, endpos))) {
                    for (String alt: record.getAlt()) {
                        String[] spl = line.split("\t");
                        if (alt.equals(spl[altColNum])) {
                            found = true;
                            if (colNum > -1) {
                                // annotate based on a column value
                                if (spl.length <= colNum) {
                                    throw new VCFAnnotatorException("Missing column for line: " + line);
                                }
                                // TODO: HERE
                                if (!spl[colNum].equals("")) {
                                	vals.add(spl[colNum]);
                                }
                            }
                        }
                    }
                }
            } else {
                // just look for a BED region that spans this VCF position
                found = false;
                for (String line : IterUtils.wrap(tabix.query(chrom, pos - 1, endpos))) {
                    found = true;
                    if (colNum > -1) { 
                        // annotate based on a column value
                        String[] spl = line.split("\t");

                        if (spl.length <= colNum) {
                            throw new VCFAnnotatorException("Missing column for line: " + line);
                        }
                        // TODO: HERE
                        if (!spl[colNum].equals("")) {
                            vals.add(spl[colNum]);
                        }
                    }
                }
            }

            if (found) {
                if (colNum == -1) {
                    // this is just a flag
                    record.getInfo().putFlag(name);
                } else {
                    if (vals.size() > 0) {
                    	// don't add empty annotations
//                        record.getInfo().put(name, VCFAttributeValue.MISSING);
//                    } else {
                        // TODO: replace empty strings for missing?
                    	try {
	                        if (collapse) {
	                            record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", StringUtils.unique(vals))));
	                        } else {
	                            record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
	                        }
	            		} catch (VCFAttributeException e) {
	            			throw new VCFAnnotatorException(e);
	            		}
                    }
                }
            }
        } catch (IOException | DataFormatException e) {
            throw new VCFAnnotatorException(e);
        }
    }
}