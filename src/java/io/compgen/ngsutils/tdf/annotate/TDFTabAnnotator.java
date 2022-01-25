package io.compgen.ngsutils.tdf.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.tdf.TabDelimitedFile;

public class TDFTabAnnotator implements TDFAnnotator {
    protected String fname;
    protected String keyCol;
    protected String valCol;
    protected String name;
    
    
    boolean collapse = false;
    boolean first = false;
    boolean csv = false;
    boolean noheader = false;
    boolean headerComment = false;

    
    Map<String, String> values = new HashMap<String, String>();
    
    
    public TDFTabAnnotator(String fname, String keyCol, String valCol, String name) {
		this.fname = fname;
		this.keyCol = keyCol;
		this.valCol = valCol;
		this.name = name;
	}
	public void setCollapse() {
		this.collapse = true;
	}
	public void setFirst() {
		this.first = true;
	}
	public void setCSV() {
		this.csv = true;
	}
	public void setNoHeader() {
		this.noheader = true;
	}
	public void setHeaderComment() {
		this.headerComment = true;		
	}

	
	@Override
	public void validate() throws TDFAnnotationException {
		try {
			TabDelimitedFile tdf = new TabDelimitedFile(fname, !noheader, headerComment);
		    int keyColIdx = -1;
		    int valColIdx = -1;

		    try {
		    	keyColIdx = Integer.parseInt(keyCol)-1;
		    } catch (NumberFormatException e) {
			    keyColIdx = tdf.findColumnByName(keyCol);
		    }

		    try {
			    valColIdx = Integer.parseInt(valCol)-1;
		    } catch (NumberFormatException e) {
		    	valColIdx = tdf.findColumnByName(valCol);
		    }
		    
		    for (String[] line: IterUtils.wrap(tdf.lines())) {
		    	if (line.length <= keyColIdx) {
					throw new TDFAnnotationException("Line missing key column (too short)");
		    	}
		    	if (line.length > valColIdx) {
		    		values.put(line[keyColIdx], line[valColIdx]);
		    	} else {
		    		// missing value column -- skip it.
		    	}
		    }
		} catch (IOException e) {
			throw new TDFAnnotationException(e);
		}
		
//		for (String k:values.keySet()) {
//			System.err.println(k + " => " + values.get(k));
//		}
		
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public String getValue(String key) throws TDFAnnotationException {
		if (csv) {
			List<String> ret = new ArrayList<String>();
			for (String k: key.split(",")) {
				if (values.containsKey(k)) {
					ret.add(values.get(k));
				}				
			}
			
			if (first) {
				if (ret.size()>0) {
					return ret.get(0);
				} else {
					return null;
				}
			} else if (collapse) {
				List<String> ret2 = new ArrayList<String>();
				for (String r:ret) {
					if (!ret2.contains(r)) {
						ret2.add(r);
					}
				}
				if (ret2.size()>0) {
					return StringUtils.join(",", ret2);
				} else {
					return null;
				}
			} else {
				if (ret.size()>0) {
					return StringUtils.join(",", ret);
				} else {
					return null;
				}

			}
			
		} else if (values.containsKey(key)) {
			return values.get(key);
		}
		return null;
	}


}
