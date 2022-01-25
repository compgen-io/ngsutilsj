package io.compgen.ngsutils.tdf.annotate;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

public class InFileAnnotator implements TDFAnnotator {
    protected String fname;
    protected String name;
    
    
    boolean collapse = false;
    boolean first = false;
    boolean csv = false;
    boolean noheader = false;
    boolean headerComment = false;

    
    Set<String> values = new HashSet<String>();
    
    
    public InFileAnnotator(String fname, String name) {
		this.fname = fname;
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

	
	@Override
	public void validate() throws TDFAnnotationException {
		try {
			StringLineReader reader = new StringLineReader(new FileInputStream(this.fname));
			for (String line: reader) {
				values.add(StringUtils.strip(line));
			}
			reader.close();
			
		} catch (IOException e) {
			throw new TDFAnnotationException(e);
		}
		
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
				if (values.contains(k)) {
					ret.add(this.name);
				}
			}
			
			if (first) {
				if (ret.size()>0) {
					return ret.get(0);
				} else {
					return null;
				}
			} else if (collapse) {
				if (ret.size()>0) {
					return StringUtils.join(",", StringUtils.unique(ret));
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
		} else if (values.contains(key)) {
			return this.name;
		}
		return null;
	}


}
