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
    
    protected int col = -1;
    protected int altCol = -1;
    protected int refCol = -1;

    protected boolean isNumber = false;
    protected boolean max = false;
    protected boolean first = false;
    protected boolean collapse = false;
    protected boolean noHeader = false;
    protected int extend = 0;

    protected String colDefString;
    
    final protected String sampleName;
    protected int sampleNum = -1;

    
	// INFO annotation
    public TabixAnnotation(String name, String filename) throws IOException {
    	this(name, filename, null);
    }
 
	// FORMAT annotation (or ctor)
    public TabixAnnotation(String name, String filename, String sampleName) throws IOException {
	    this.name = name;
	    this.filename = filename;
	    this.sampleName = sampleName;
        this.tabix = getTabixFile(filename);
    }
 

    private static TabixFile getTabixFile(String filename) throws IOException {
        if (!cache.containsKey(filename)) {
            cache.put(filename, new TabixFile(filename));
        }
        return cache.get(filename);
    }

    @Override
    public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
    	if (sampleName != null) {
		    sampleNum = header.getSamplePosByName(sampleName);
		    if (sampleNum < 0) {
				throw new VCFAnnotatorException("Missing sample: "+sampleName);
		    }
    	}
    	
    	if (noHeader) {
    		return;
    	}
        try {
            if (col == -1) {
                header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in Tabix file",
                        filename, null, null, null));
            } else {
            	String type = "String";
            	String desc;
            	
            	if (isNumber) {
            		type = "Float";
            	}
            	
            	if (this.colDefString != null) {
            		desc = "Column " + VCFHeader.quoteString(colDefString) + " from file";
            	} else {
            		desc = "Column " + (col+1) + " from file";
            	}

            	if (sampleName != null) {
            		// FORMAT
            		header.addFormat(VCFAnnotationDef.format(name,  ".", type, desc, filename, null, null, null));
            	} else {
            		header.addInfo(VCFAnnotationDef.info(name,  ".", type, desc, filename, null, null, null));
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
            if (refCol>-1) {
            	// we are matching against a ref/alt column, so don't adjust the positions. This input should be equivalent to a VCF comparison.
            	pos = record.getPos();
            	endpos = record.getPos();
            	
            } else {
	            pos = getPos(record);
	            endpos = getEndPos(record);
            }
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
            
            for (String line : IterUtils.wrap(tabix.query(chrom, pos - 1 - extend, endpos + extend))) {
                String[] spl = line.split("\t");
                
                boolean altOk = true;
                if (altCol > -1) {
                	altOk = false;
                    for (String alt: record.getAlt()) {
                        if (alt.equals(spl[altCol])) {
                        	altOk = true;
                        }
                    }
                }
                if (!altOk) {
                	continue;
                }

                boolean refOk = true;
                if (refCol > -1) {
                	refOk = false;
                    if (record.getRef().equals(spl[refCol])) {
                    	refOk = true;
                    }
                }
                if (!refOk) {
                	continue;
                }

                found = true;
                if (col > -1) {
                    // annotate based on a column value
                    if (spl.length>col) {
                        if (!spl[col].equals("")) {
                        	vals.add(spl[col]);
                        }
                    } else {
                    	// do nothing for blanks...
                        //throw new VCFAnnotatorException("Missing column for line: " + line);
                    }
                }
            }
            
            if (found) {
                if (col == -1) {
                    // this is just a flag
                    record.getInfo().putFlag(name);
                } else {
                    if (vals.size() > 0) {
        				if (sampleName != null) {
	                    	try {
		                        if (collapse) {
		                        	record.getSampleAttributes().get(sampleNum).put(name, new VCFAttributeValue(StringUtils.join(",", StringUtils.unique(vals))));
		                        } else if (first) {
		                        	record.getSampleAttributes().get(sampleNum).put(name, new VCFAttributeValue(vals.get(0)));
		                        } else if (max) {
		                        	double[] vals_d = new double[vals.size()];
		                        	for (int i=0; i<vals.size(); i++) {
		                        		vals_d[i] = Double.parseDouble(vals.get(i));
		                        	}
		                        	
		                        	double maxD = vals_d[0];
		                        	for (double d: vals_d) {
		                        		if (d > maxD) {
		                        			maxD = d;
		                        		}
		                        	}
		                        	String maxS = ""+maxD;
		                        	if (maxS.endsWith(".0")) {
		                        		maxS = maxS.substring(0, maxS.length()-2);
		                        	}
		                        	record.getSampleAttributes().get(sampleNum).put(name, new VCFAttributeValue(maxS));
		                        } else {
		                        	record.getSampleAttributes().get(sampleNum).put(name, new VCFAttributeValue(StringUtils.join(",",vals)));
		                        }
		            		} catch (VCFAttributeException e) {
		            			throw new VCFAnnotatorException(e);
		            		}
                    	} else {
	                    	try {
		                        if (collapse) {
		                            record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", StringUtils.unique(vals))));
		                        } else if (first) {
		                            record.getInfo().put(name, new VCFAttributeValue(vals.get(0)));
		                        } else if (max) {
		                        	double[] vals_d = new double[vals.size()];
		                        	for (int i=0; i<vals.size(); i++) {
		                        		vals_d[i] = Double.parseDouble(vals.get(i));
		                        	}
		                        	
		                        	double maxD = vals_d[0];
		                        	for (double d: vals_d) {
		                        		if (d > maxD) {
		                        			maxD = d;
		                        		}
		                        	}
		                        	String maxS = ""+maxD;
		                        	if (maxS.endsWith(".0")) {
		                        		maxS = maxS.substring(0, maxS.length()-2);
		                        	}

		                        	record.getInfo().put(name, new VCFAttributeValue(maxS));
		                        } else {
		                            record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
		                        }
		            		} catch (VCFAttributeException e) {
		            			throw new VCFAnnotatorException(e);
		            		}
                    	}
                    }
                }
            }
        } catch (IOException | DataFormatException e) {
            throw new VCFAnnotatorException(e);
        }
    }


	public void setCol(int colNum) {
		this.col = colNum;
	}

	public void setCol(String colName) throws IOException {
		this.col = tabix.findColumnByName(colName);
		this.colDefString = colName;
	}


	public void setAltCol(int altCol) {
		this.altCol = altCol;
	}
	public void setAltCol(String altCol) throws IOException {
		this.altCol = tabix.findColumnByName(altCol);
	}


	public void setRefCol(int refCol) {
		this.refCol = refCol;
	}
	public void setRefCol(String refCol) throws IOException {
		this.refCol = tabix.findColumnByName(refCol);
	}


	public void setNumber() {
		this.isNumber = true;
	}


	public void setMax() {
		this.max = true;
	}

	public void setFirst() {
		this.first = true;
	}

	public void setCollapse() {
		this.collapse = true;
	}

	public void setExtend(int extend) {
		this.extend = extend;
	}


	public void setNoHeader() {
		this.noHeader = true;
	}


	public void setColDefString(String colDefString) {
		this.colDefString = colDefString;
	}
}