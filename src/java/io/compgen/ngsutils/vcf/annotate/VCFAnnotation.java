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
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class VCFAnnotation extends AbstractBasicAnnotator {
	private static Map<String, BGZFile> cache = new HashMap<String, BGZFile>();
	
	final protected String name;
	final protected String filename;
	final protected BGZFile bgzf;
	final protected String infoVal;
	final protected boolean exactMatch;
	
	public VCFAnnotation(String name, String filename, String infoVal, boolean exact) throws IOException {
		this.name = name;
		this.filename = filename;
		this.infoVal = infoVal;
		this.exactMatch = exact;
		this.bgzf = getBGZFile(filename);
	}	

	public VCFAnnotation(String name, String filename, String infoVal) throws IOException {
		this(name, filename, infoVal, false);
	}
	
	private static BGZFile getBGZFile(String filename) throws IOException {
		if (!cache.containsKey(filename)) {
			if (!BGZFile.isBGZFile(filename)) {
				throw new IOException("VCF file: "+filename+" is not BGZip compressed");
			}
            if (!new File(filename+".csi").exists()) {
                if (!new File(filename+".tbi").exists()) {
                    throw new IOException("VCF file: "+filename+" is missing a CSI/TBI index");
                }
            }
			cache.put(filename, new BGZFile(filename));
		}
		return cache.get(filename);		
	}
	
	@Override
	public void setHeader(VCFHeader header) throws VCFAnnotatorException {
		try {
			if (infoVal == null) {
				header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in VCF file", filename, null, null, null));
			} else {
				header.addInfo(VCFAnnotationDef.info(name, "1", "String", infoVal+" from VCF file", filename, null, null, null));
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
//			System.err.println("Looking for variants: "+ record.getChrom()+":"+(record.getPos()-1));
			String records = bgzf.query(record.getChrom(), record.getPos()-1);
//			System.err.println(records);

			if (records == null) {
				return;
			}
			
			List<String> vals = new ArrayList<String>();

			for (String line: records.split("\n")) {
//				System.err.println(">>"+line);
				VCFRecord bgzfRec = VCFRecord.parseLine(line);

				boolean match = !exactMatch;
				
				if (exactMatch) {
					for (String a1: bgzfRec.getAlt()) {
						for (String a2: record.getAlt()) {
							if (a1.equals(a2)) { 
								match = true;
							}
						}
					}
				}
				
				if (match) {
					if (infoVal == null) { // just add a flag
						record.getInfo().put(name, VCFAttributeValue.EMPTY);
						return;
					}
					if (bgzfRec.getInfo().get(infoVal)!=null) {
						vals.add(bgzfRec.getInfo().get(infoVal).asString(null));
					}
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