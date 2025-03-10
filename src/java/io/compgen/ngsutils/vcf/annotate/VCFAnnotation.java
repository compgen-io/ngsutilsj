package io.compgen.ngsutils.vcf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.DataFormatException;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.cache.LRUCache;
import io.compgen.ngsutils.tabix.TabixFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class VCFAnnotation extends AbstractBasicAnnotator {
	private static Map<String, TabixFile> cache = new HashMap<String, TabixFile>();
	private static LRUCache<String, VCFRecord> recCache = new LRUCache<String, VCFRecord>();
	
	final protected String name;
	final protected String filename;
	final protected TabixFile vcfTabix;
	final protected String infoVal;
    final protected boolean exactMatch;
    final protected boolean passingOnly;
    final protected boolean uniqueMatch;
    final protected boolean noheader;
	
	public VCFAnnotation(String name, String filename, String infoVal, boolean exact, boolean passing, boolean unique, boolean noheader) throws IOException {
		this.name = name;
		this.filename = filename;
		this.infoVal = infoVal;
		if (name.equals("@ID")) {
		    this.exactMatch = true;
		} else {
		    this.exactMatch = exact;
		}
		
		this.passingOnly = passing;
		this.uniqueMatch = unique;
		this.noheader = noheader;
		this.vcfTabix = getTabixFile(filename);
	}	

	public VCFAnnotation(String name, String filename, String infoVal) throws IOException {
		this(name, filename, infoVal, false, false, false, false);
	}
	
	private static TabixFile getTabixFile(String filename) throws IOException {
		if (!cache.containsKey(filename)) {
			cache.put(filename, new TabixFile(filename));
		}
		return cache.get(filename);		
	}
	
	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		if (this.noheader) {
			return;
		}
		try {
		    if (name.equals("@ID")) {
		        return;
		    }
		    
		    String extra = "";
            if (passingOnly) {
                extra = "passing";
            }
            if (exactMatch) {
            	if (!extra.equals("")) {
            		extra += ",";
            	}
                extra += "exact";
		    }
            if (uniqueMatch) {
            	if (!extra.equals("")) {
            		extra += ",";
            	}
                extra += "unique";
		    }
        	if (!extra.equals("")) {
        		extra = " (" + extra+")";
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
            pos = record.getPos(); // don't adjust the position based on deletions -- for a VCF:VCF comparison, the pos/ref/alt should match exactly.
        } catch (VCFAnnotatorMissingAltException e) {
            return;
        }
//        System.err.println("Query: " + chrom+":"+pos + " File: "+filename);

	    try {
//			String records = ; // zero-based query
//
//			if (records == null) {
//				return;
//			}
			
			List<String> vals = new ArrayList<String>();

//			System.err.println("VCF query: " + chrom+":" + pos);
			for (String line: IterUtils.wrap(vcfTabix.query(chrom, pos-1))) {

				// Treat this as a LRUCache so that we don't re-parse the same line again (can be expensive)
				VCFRecord bgzfRec = recCache.get(line);
				if (bgzfRec == null) {
					bgzfRec = VCFRecord.parseLine(line);
					recCache.put(line, bgzfRec);
				}

//                System.err.println("Record: " + bgzfRec.getChrom()+":"+bgzfRec.getPos() + " -> "+filename);

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
				    if (bgzfRec.getRef()!=null && bgzfRec.getRef().equals(record.getRef())) {
					    if (bgzfRec.getAlt()!=null) {
	    					for (String a1: bgzfRec.getAlt()) {
	    						if (record.getAlt() != null) {
		    						for (String a2: record.getAlt()) {
		    							if (a1.equals(a2)) { 
		    								match = true;
		    								break;
		    							}
		    						}
	    						}
	    						if (match) {
	    							break;
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
				    } else if (infoVal.equals("@ID")) { // just add a flag
				    	vals.add(bgzfRec.getDbSNPID());
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
				if (uniqueMatch) {
					Set<String> tmp = new TreeSet<String>();
					tmp.addAll(vals);
					record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", tmp)));
				} else {
					record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
				}
			}
		} catch (VCFAttributeException | VCFParseException | IOException | DataFormatException e) {
			throw new VCFAnnotatorException(e);
		}
	}
}