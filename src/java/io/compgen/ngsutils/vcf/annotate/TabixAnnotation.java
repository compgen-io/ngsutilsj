package io.compgen.ngsutils.vcf.annotate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tabix.BGZFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class TabixAnnotation extends AbstractBasicAnnotator {
    private static Map<String, BGZFile> cache = new HashMap<String, BGZFile>();

    final protected String name;
    final protected String filename;
    final protected BGZFile bgzf;
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
        this.bgzf = getBGZFile(filename);
    }

    public TabixAnnotation(String name, String filename, int colNum, boolean isNumber)
            throws IOException {
        this(name, filename, colNum, isNumber, -1);
    }

    public TabixAnnotation(String name, String filename) throws IOException {
        this(name, filename, -1, false, -1);
    }

    private static BGZFile getBGZFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            if (!BGZFile.isBGZFile(filename)) {
                throw new IOException("VCF file: " + filename + " is not BGZip compressed");
            }
            if (!new File(filename + ".csi").exists()) {
                if (!new File(filename + ".tbi").exists()) {
                    throw new IOException(
                            "VCF file: " + filename + " is missing a CSI/TBI index");
                }
            }
            cache.put(filename, new BGZFile(filename));
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
            bgzf.close();
        } catch (IOException e) {
            throw new VCFAnnotatorException(e);
        }
    }

    @Override
    public void annotate(VCFRecord record) throws VCFAnnotatorException {
        try {
            String tabix = bgzf.query(record.getChrom(), record.getPos() - 1);

            if (tabix == null) {
                return;
            }

            List<String> vals = new ArrayList<String>();
            boolean found = false;

            for (String alt: record.getAlt()) {
                
                // for each alt -- process each line; 
                // that way the order of the results will be consistent
                
                for (String line : tabix.split("\n")) {
                    found = true;
                    if (colNum > -1) {
                        String[] spl = line.split("\t");
    
                        if (spl.length <= colNum) {
                            throw new VCFAnnotatorException("Missing column for line: " + line);
                        }
                        
                        if (altColNum > -1) {
                            if (alt.equals(spl[altColNum])) {
                                vals.add(spl[colNum]);
                            }
                        } else {
                            vals.add(spl[colNum]);
                        }
                    }
                }
            }

            if (colNum == -1) {
                if (found) {
                    record.getInfo().put(name, VCFAttributeValue.EMPTY);
                }
            } else {
                if (vals.size() == 0) {
                    record.getInfo().put(name, VCFAttributeValue.MISSING);
                } else {
                    record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
                }
            }
        } catch (IOException | DataFormatException e) {
            throw new VCFAnnotatorException(e);
        }
    }
}