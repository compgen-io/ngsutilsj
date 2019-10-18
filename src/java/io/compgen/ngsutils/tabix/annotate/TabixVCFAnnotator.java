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
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class TabixVCFAnnotator implements TabAnnotator {
    private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();
    
    final protected String name;
    final protected String filename;
    final protected TabixFile vcfTabix;
    final protected String infoVal;
    final protected boolean passingOnly;
    final protected int altCol;
    final protected int refCol;
    
    public TabixVCFAnnotator(String name, String filename, String infoVal, boolean passing, int refCol, int altCol) throws IOException {
        this.name = name;
        this.filename = filename;
        if (infoVal != null && !infoVal.equals("")) {
            this.infoVal = infoVal; 
        } else {
            this.infoVal = null;
        }

        this.passingOnly = passing;
        this.vcfTabix = getTabixFile(filename);
        
        this.refCol = refCol;
        this.altCol = altCol;
        
    }   

    public TabixVCFAnnotator(String name, String filename, String infoVal) throws IOException {
        this(name, filename, infoVal, false, -1, -1);
    }
    
    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);     
    }
    
    @Override
    public void close() throws IOException {
        vcfTabix.close();
    }

    @Override
    public String getValue(String chrom, int start, int end, String[] qCols) throws IOException {
//        System.err.println("Query: " + chrom+":"+start);

        try {
//          String records = ; // zero-based query
//
//          if (records == null) {
//              return;
//          }
            
            List<String> vals = new ArrayList<String>();

            for (String line: IterUtils.wrap(vcfTabix.query(chrom, start, end))) {
                VCFRecord bgzfRec = VCFRecord.parseLine(line);

//                System.err.println("Record: " + bgzfRec.getChrom()+":"+bgzfRec.getPos());

                if (bgzfRec.getPos() != start || bgzfRec.getPos() != end) {
//                  System.err.println("Wrong pos?" + start + " vs " +  bgzfRec.getPos());
                    // exact pos matches only...
                    
                    // don't check chrom to avoid potential chrZ/Z mismatches 
                    // the tabix query handles this.
                    continue;
                }
                
                if (passingOnly && bgzfRec.isFiltered()) {
//                    System.err.println("Filter fail: " + StringUtils.join(",", bgzfRec.getFilters()));
                    continue;
                }

                boolean match = true;
                
                if (altCol > -1 && refCol > -1) {
                    match = false;
                    if (bgzfRec.getRef().toUpperCase().equals(qCols[refCol-1].toUpperCase())) {
                        if (bgzfRec.getAlt()!=null) {
                            for (String a1: bgzfRec.getAlt()) {
                                for (String a2: qCols[altCol-1].split(",")) {
                                    if (a1.toUpperCase().equals(a2.toUpperCase())) { 
                                        match = true;
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (match) {
                    if (name.equals("@ID")) {
                        return bgzfRec.getDbSNPID();
                    } else if (infoVal == null) { // just add a flag
                        return name;
                    } else {
                       if (bgzfRec.getInfo().get(infoVal)!=null) {
                           String val = bgzfRec.getInfo().get(infoVal).asString(null);
                           if (val != null && !val.equals("")) {
                               vals.add(val);
                           } else if (bgzfRec.getInfo().get(infoVal).equals(VCFAttributeValue.EMPTY)) {
                               vals.add(infoVal);
                           }
                       }
                    }
//                } else {
//                    System.err.println("No match??");
                }
            }
            // it's possible for there to be multiple lines for a position, so we need to loop them all
            if (vals.size() > 0) {
                return StringUtils.join(",", vals);
            } else {
                return "";
            }
        } catch (VCFAttributeException | VCFParseException | IOException | DataFormatException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getName() {
        return name;
    }
}