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

public class VCFAnnotation extends AbstractBasicAnnotator {
	private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();
	
	final protected String name;
	final protected String filename;
	final protected TabixFile vcfTabix;
	final protected String infoVal;
    final protected boolean exactMatch;
    final protected boolean passingOnly;
	
	public VCFAnnotation(String name, String filename, String infoVal, boolean exact, boolean passing) throws IOException {
		this.name = name;
		this.filename = filename;
		this.infoVal = infoVal;
		if (name.equals("@ID")) {
		    this.exactMatch = true;
		} else {
		    this.exactMatch = exact;
		}
		
		this.passingOnly = passing;
		this.vcfTabix = getTabixFile(filename);
	}	

	public VCFAnnotation(String name, String filename, String infoVal) throws IOException {
		this(name, filename, infoVal, false, false);
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
		    if (name.equals("@ID")) {
		        return;
		    }
		    
		    String extra = "";
		    if (passingOnly && exactMatch) {
                extra = " (passing, exact match)";
            } else if (passingOnly) {
                extra = " (passing)";
            } else if (exactMatch) {
                extra = " (exact match)";
		    }
		    
			if (infoVal == null) {
		        header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in VCF file"+extra, filename, null, null, null));
			} else {
                header.addInfo(VCFAnnotationDef.info(name, "1", "String", infoVal+" from VCF file"+extra, filename, null, null, null));
			}
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}
	
	@Override
	public void close() throws VCFAnnotatorException {
		try {
			vcfTabix.close();
		} catch (IOException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
        String chrom;
        int pos;
        try {
            chrom = getChrom(record);
            pos = getPos(record);
        } catch (VCFAnnotatorMissingAltException e) {
            return;
        }
//        System.err.println("Query: " + chrom+":"+pos);

	    try {
//			String records = ; // zero-based query
//
//			if (records == null) {
//				return;
//			}
			
			List<String> vals = new ArrayList<String>();

//			System.err.println("VCF query: " + chrom+":" + pos);
			for (String line: IterUtils.wrap(vcfTabix.query(chrom, pos-1))) {
				VCFRecord bgzfRec = VCFRecord.parseLine(line);

//                System.err.println("Record: " + bgzfRec.getChrom()+":"+bgzfRec.getPos());

				if (bgzfRec.getPos() != record.getPos()) {
//				    System.err.println("Wrong pos?" + record.getPos() + " vs " +  bgzfRec.getPos());
				    // exact pos matches only...
				    
				    // don't check chrom to avoid potential chrZ/Z mismatches 
				    // the tabix query handles this.
				    
				    continue;
				}

				
                if (passingOnly && bgzfRec.isFiltered()) {
//                    System.err.println("Filter fail: " + StringUtils.join(",", bgzfRec.getFilters()));
                    continue;
                }

				boolean match = !exactMatch;
				
				if (exactMatch) {
				    if (bgzfRec.getAlt()!=null) {
    					for (String a1: bgzfRec.getAlt()) {
    						for (String a2: record.getAlt()) {
    							if (a1.equals(a2)) { 
    								match = true;
    							}
    						}
    					}
				    }
				}
				
				if (match) {
				    if (name.equals("@ID")) {
                        record.setDbSNPID(bgzfRec.getDbSNPID());
                        // @ID returns right away
                        return;
				    } else if (infoVal == null) { // just add a flag
//				        System.err.println("Flagged: " + name);
                        record.getInfo().put(name, VCFAttributeValue.EMPTY);
                        // flags return right away
                        return;
					} else {
	                   if (bgzfRec.getInfo().get(infoVal)!=null) {
	                       String val = bgzfRec.getInfo().get(infoVal).asString(null);
	                       if (val != null && !val.equals("")) {
	                           vals.add(val);
	                       }
	                    }
					}
					
//				} else {
//                    System.err.println("Alt match fail: " + record.getAlt() +  " vs " + bgzfRec.getAlt());
				}
		
            }
			// it's possible for there to be multiple lines for a position, so we need to loop them all
			if (vals.size() > 0) {
				record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
			}
		} catch (VCFAttributeException | VCFParseException | IOException | DataFormatException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}