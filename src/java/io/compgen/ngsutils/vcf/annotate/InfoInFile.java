package io.compgen.ngsutils.vcf.annotate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class InfoInFile extends AbstractBasicAnnotator {

	protected String filename;
	protected String flagName;
	protected String tagName;
	protected String delimiter = null;
	protected Map<String, String> values = new HashMap<String, String>();
	
	public InfoInFile(String filename, String tagName, String flagName) throws IOException {
		this(filename, tagName, flagName, null, -1);
	}
	
	/**
	 * 
	 * @param filename filename to read (either a gene/value list or tab-delimited)
	 * @param tagName this is the tag to read from the VCF file
	 * @param flagName add this FLAG/TAG to the VCF (FLAG=col if col given)
	 * @param delimiter if the TAG in the VCF file can contain more than one entry, what is the delimiter (comma?)
	 * @param colnum the input file is a tab delimited file with the first column as the key... add this other column as the TAG to the VCF file (1-based). 
	 * @throws IOException
	 */
	public InfoInFile(String filename, String tagName, String flagName, String delimiter, int colnum) throws IOException {
		this.filename = filename;
		this.flagName = flagName;
		this.tagName = tagName;
		if (delimiter.equals("")) {
			this.delimiter = null;
		} else {
			this.delimiter = delimiter;
		}
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.length() > 0 && line.charAt(0) == '#') { 
				//skip comments
				continue;
			}
			if (colnum > -1) {
				String[] cols = line.trim().split("\t");
				values.put(cols[0], cols[colnum-1]); // colnum is 1-based
			} else {
				values.put(line.trim(), null);
			}
		}
		reader.close();
	}
	
	public VCFAnnotationDef getAnnotationType() throws VCFAnnotatorException {
		try {
			return VCFAnnotationDef.info(flagName, "0", "Flag", "Is value "+tagName+" present in file", filename, null, null, null);
		} catch (VCFParseException e) {
			throw new VCFAnnotatorException(e);
		}
	}

	@Override
	public void setHeaderInner(VCFHeader header) throws VCFAnnotatorException {
		header.addFormat(getAnnotationType());
	}
	
	protected void annotate(VCFRecord record) throws VCFAnnotatorException {
		VCFAttributeValue ann = record.getInfo().get(tagName);
		if (ann !=null) {
			String val;
			try {
				val = ann.asString(null);
			} catch (VCFAttributeException e) {
				throw new VCFAnnotatorException(e);
			}
			
			if (!val.equals(VCFAttributeValue.MISSING.toString())) {
				if (delimiter == null) {
					if (values.containsKey(val.trim())) {
						if (values.get(val.trim()) == null) {
							record.getInfo().putFlag(flagName);
						} else {
							try {
								String val2 = values.get(val.trim());
								if (val2 != null && !val2.equals("")) {
									record.getInfo().put(flagName, new VCFAttributeValue(val2));
								}
							} catch (VCFAttributeException e) {
								throw new VCFAnnotatorException(e);
							}
						}
					}
				} else {
					for (String spl: val.split(delimiter)) {
						if (values.containsKey(spl.trim())) {
							if (values.get(spl.trim()) == null) {
								record.getInfo().putFlag(flagName);
							} else {
								try {
									String val2 = values.get(spl.trim());
									if (val2 != null && !val2.equals("")) {
										record.getInfo().put(flagName, new VCFAttributeValue(val2));
									}
								} catch (VCFAttributeException e) {
									throw new VCFAnnotatorException(e);
								}
							}
						}
					}
				}
			}
			
		}
	}
}