package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.TabixFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
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

    public TabixAnnotation(String name, String filename, int colNum, boolean isNumber, int altColNum)
            throws IOException {
        this.name = name;
        this.filename = filename;
        this.colNum = colNum;
        this.isNumber = isNumber;
        this.altColNum = altColNum;
        this.tabix = getTabixFile(filename);
    }

    public TabixAnnotation(String name, String filename, int colNum, boolean isNumber)
            throws IOException {
        this(name, filename, colNum, isNumber, -1);
    }

    public TabixAnnotation(String name, String filename) throws IOException {
        this(name, filename, -1, false, -1);
    }

    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    @Override
    public void setHeader(VCFHeader header) throws VCFAnnotatorException {
        try {
            if (colNum == -1) {
                header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in Tabix file",
                        filename, null, null, null));
            } else {
                if (isNumber) {
                    header.addInfo(VCFAnnotationDef.info(name, ".", "Float",
                            "Column " + colNum + " from file", filename, null, null, null));
                } else {
                    header.addInfo(VCFAnnotationDef.info(name, ".", "String",
                            "Column " + colNum + " from file", filename, null, null, null));
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
        try {
            
//            System.err.println("Looking for TABIX rows covering: "+record.getChrom() +":"+ record.getPos()+" ("+filename+")");
            String tabixLines = tabix.query(record.getChrom(), record.getPos() - 1);

            if (tabixLines == null) {
//                System.err.println("Not found");
                return;
            }

            List<String> vals = new ArrayList<String>();
            boolean found = false;

            for (String alt: record.getAlt()) {
//                System.err.println("  Alt: "+alt);
                                
                // for each alt -- process each line; 
                // that way the order of the results will be consistent
                
                for (String line : tabixLines.split("\n")) {
//                    System.err.println("  Line: "+line);
                    found = true;
                    if (colNum > -1) {
                        String[] spl = line.split("\t");
    
                        if (spl.length <= colNum) {
                            throw new VCFAnnotatorException("Missing column for line: " + line);
                        }
                        
                        if (altColNum > -1) {
                            if (alt.equals(spl[altColNum])) {
//                                System.err.println("  alt match: "+ alt +"="+spl[altColNum]);
//                                System.err.println("        val: "+ spl[colNum]);
                                vals.add(spl[colNum]);
                            }
                        } else {
//                            System.err.println("        val: "+ spl[colNum]);
                            vals.add(spl[colNum]);
                        }
                    }
                }
            }

            if (colNum == -1) {
                if (found) {
//                    System.err.println("  FLAG");
                    record.getInfo().put(name, VCFAttributeValue.EMPTY);
                }
            } else {
                if (vals.size() == 0) {
//                    System.err.println("  MISSING");
                    record.getInfo().put(name, VCFAttributeValue.MISSING);
                } else {
//                    System.err.println("  VALUES: "+StringUtils.join(",", vals));
                    record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
                }
            }
        } catch (IOException | DataFormatException e) {
            throw new VCFAnnotatorException(e);
        }
    }
}