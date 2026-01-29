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
import io.compgen.ngsutils.tabix.TabixFile;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFRecord;

public class VCFAnnotation extends AbstractBasicAnnotator {
	public class VCFRecordCache {
		private VCFRecord curRecord;
		private List<VCFRecord> curList;
		public List<VCFRecord> get(VCFRecord record) {
			// yes -- an exact (==) match. We should be passing around
			//        the same object, not copies or different
			//        instances with the same coordinates
			if (record == curRecord) {
				return curList;
			}
			return null;
		}
		public void put(VCFRecord record, List<VCFRecord> list) {
			curRecord = record;
			curList = list;
		}
	}

	private static Map<String, TabixFile> cache = new HashMap<>();

	final protected String name;
	final protected String filename;
	final protected TabixFile vcfTabix;
	final protected VCFRecordCache vcfRecordCache;
	final protected String infoVal;
	final protected boolean exactMatch;
	final protected boolean passingOnly;
	final protected boolean uniqueMatch;
	final protected boolean noheader;
	
	static private Map<String, VCFRecordCache> curMatchesExact = null;
	static private Map<String, VCFRecordCache> curMatchesAll = null;

	public VCFAnnotation(String name, String filename, String infoVal, boolean exact, boolean passing, boolean unique,
			boolean noheader) throws IOException {
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
		this.vcfRecordCache = getRecordCache(filename, exact);
	}

	private VCFRecordCache getRecordCache(String filename, boolean exact) {
		// we have to store exact and position matches in separate
		// caches.
		if (exact) {
			if (!curMatchesExact.containsKey(filename)) {
				curMatchesExact.put(filename, new VCFRecordCache());
			}
			return curMatchesExact.get(filename);
		}
		if (!curMatchesAll.containsKey(filename)) {
			curMatchesAll.put(filename, new VCFRecordCache());
		}
		return curMatchesAll.get(filename);
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
				extra = " (" + extra + ")";
			}

			if (infoVal == null) {
				header.addInfo(VCFAnnotationDef.info(name, "0", "Flag", "Present in VCF file" + extra, filename, null,
						null, null));
			} else {
				header.addInfo(VCFAnnotationDef.info(name, "1", "String", infoVal + " from VCF file" + extra, filename,
						null, null, null));
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

	private List<VCFRecord> getMatchingRecordsForRec(VCFRecord record) throws VCFAnnotatorException {

		// We are going to use this as a very simple memoization cache. VCFAnnotations operate in the 
		// context of a chain. All instances of this class will operate within that singular chain, so
		// we can use a strict static cache to store the current 
		
		List<VCFRecord> curMatches = vcfRecordCache.get(record);
		if (curMatches != null) {
			return curMatches;
		}
		
		curMatches = new ArrayList<VCFRecord>();

		String chrom;
		int pos;
		try {
			// don't adjust the position (or end) based on deletions
			// for a VCF:VCF comparison, the pos/ref/alt should match exactly.
			// for other annotation types, adjusting the end pos might be correct
			chrom = getChrom(record);
			pos = record.getPos();

			// this query is also cached by TabixFile (and the BGZ chunk is also cached)
			// but for VCF files, we'll effectively only call this once per annotation chain.
			
			for (String line : IterUtils.wrap(vcfTabix.query(chrom, pos - 1))) {
				VCFRecord bgzfRec = VCFRecord.parseLine(line);

				// System.err.println("Record: " + bgzfRec.getChrom()+":"+bgzfRec.getPos() + "
				// -> "+filename);

				if ((bgzfRec.getPos() != record.getPos()) || (passingOnly && bgzfRec.isFiltered())) {
					// System.err.println("Filter fail: " + StringUtils.join(",",
					// bgzfRec.getFilters()));
					continue;
				}

				boolean match = !exactMatch;

				if (exactMatch) {
					if (bgzfRec.getRef() != null && bgzfRec.getRef().equals(record.getRef())) {
						if (bgzfRec.getAlt() != null) {
							for (String a1 : bgzfRec.getAlt()) {
								if (record.getAlt() != null) {
									for (String a2 : record.getAlt()) {
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
					curMatches.add(bgzfRec);
				}
			}
		} catch (VCFParseException | IOException | DataFormatException | VCFAnnotatorMissingAltException e) {
			throw new VCFAnnotatorException(e);
		}

		vcfRecordCache.put(record, curMatches);
		return curMatches;
	}

	@Override
	public void annotate(VCFRecord record) throws VCFAnnotatorException {
		try {
		List<String> vals = new ArrayList<>();
		for (VCFRecord bgzfRec : getMatchingRecordsForRec(record)) {
			if (name.equals("@ID")) {
				record.setDbSNPID(bgzfRec.getDbSNPID());
				// @ID returns right away
				return;
			} else if (infoVal == null) { // just add a flag
//		        System.err.println("Flagged: " + name);
				record.getInfo().put(name, VCFAttributeValue.EMPTY);
				// flags return right away
				return;
			} else if (infoVal.equals("@ID")) { // just add a flag
				vals.add(bgzfRec.getDbSNPID());
			} else {
				if (bgzfRec.getInfo().get(infoVal) != null) {
					String val = bgzfRec.getInfo().get(infoVal).asString(null);
					if (val != null && !val.equals("")) {
						vals.add(val);
					}
				}
			}
		}

		// it's possible for there to be multiple lines for a position, so we need to
		// loop them all
		if (vals.size() > 0) {
			if (uniqueMatch) {
				Set<String> tmp = new TreeSet<>();
				tmp.addAll(vals);
				record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", tmp)));
			} else {
				record.getInfo().put(name, new VCFAttributeValue(StringUtils.join(",", vals)));
			}
		}
	} catch (VCFAttributeException e) {
		throw new VCFAnnotatorException(e);
	} finally {}
	}
}