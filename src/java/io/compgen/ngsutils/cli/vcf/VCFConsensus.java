package io.compgen.ngsutils.cli.vcf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.IterUtils.EachPair;
import io.compgen.common.ListBuilder;
import io.compgen.common.Pair;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFAltDef;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFContigDef;
import io.compgen.ngsutils.vcf.VCFFilterDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFRecord.VCFAltPos;
import io.compgen.ngsutils.vcf.VCFRecord.VCFSVConnection;
import io.compgen.ngsutils.vcf.VCFRecord.VCFVarType;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-consensus", desc="For a set of VCF files, extract consensus variants (SNV and SV)", category="vcf", experimental=true)
public class VCFConsensus extends AbstractOutputCommand {
	

	private class SVVCFCoord {
		public SVVCFCoord(String ref1, int pos1, VCFAltPos alt, boolean imprecise, String vcfName, VCFRecord record, boolean inversion) {
			this.ref1 = ref1;
			this.conn = alt.connType;
			this.type = alt.type;
			this.imprecise = imprecise;
			this.inversion = inversion;
			this.pos1.add(pos1);
			this.matches.add(vcfName);
			this.records.add(new Pair<String, VCFRecord>(vcfName, record));
			this.alt = alt;
			this.ref2 = alt.chrom;
			this.pos2.add(alt.pos);
		}
		
		public final String ref1;
		public final String ref2;
		public final VCFAltPos alt;
		public final List<Integer> pos1 = new ArrayList<Integer>();
		public final List<Integer> pos2 = new ArrayList<Integer>();
		public final VCFSVConnection conn;
		public final VCFVarType type;
		private boolean imprecise;
		private boolean inversion;
//		private boolean written = false;
		

		private final Set<String> matches = new HashSet<String>();
		
		// List<Pair<VCF-PREFIX, VCFRecord>>
		private final List<Pair<String,VCFRecord>> records = new ArrayList<Pair<String,VCFRecord>>();
		
//		public void setWritten() {
//			this.written = true;
//		}
//		public boolean isWritten() {
//			return this.written;
//		}
		public boolean isImprecise() {
			return this.imprecise;
		}
		public boolean isInversion() {
			return inversion;
		}
		
		
		public void addMatch(String vcfName, VCFAltPos alt, VCFRecord record, boolean imprecise, boolean inversion) {
			this.matches.add(vcfName);
			this.records.add(new Pair<String, VCFRecord>(vcfName, record));
			
			pos1.add(record.getPos());
			pos2.add(alt.pos);
			
			if (imprecise) {
				this.imprecise = true;
			}
			
			if (inversion) {
				this.inversion = true;
			}
		}
		
		public int getCount() {
			return matches.size();
		}
		
		public boolean matchLeft(String ref, int pos, boolean imprecise) {
			if (!this.ref1.equals(ref)) {
				return false;
			}
			if (this.imprecise || imprecise) {
				for (int p: this.pos1)  {
					if (Math.abs(pos - p) <= impreciseBuffer) {
						return true;
					}
				}
			} else {
				for (int p: this.pos1)  {
					if (Math.abs(pos - p) <= preciseBuffer) {
						return true;
					}
				}
			}
			return false;
		}

		public boolean matchRight(String ref, int pos, boolean imprecise) {
			if (!this.ref2.equals(ref)) {
				return false;
			}
			if (this.imprecise || imprecise) {
				for (int p: this.pos2)  {
					if (Math.abs(pos - p) <= impreciseBuffer) {
						return true;
					}
				}
			} else {
				for (int p: this.pos2)  {
					if (Math.abs(pos - p) <= preciseBuffer) {
						return true;
					}
				}
			}
			return false;
		}
//
//		public int getPos1() {
//			long acc = 0;
//			
//			for (int p: pos1) {
//				acc += p;
//			}
//			
//			return (int) Math.ceil(acc / pos1.size());
//		}
//
//		public int getPos2() {
//			long acc = 0;
//			
//			for (int p: pos2) {
//				acc += p;
//			}
//			
//			return (int) Math.ceil(acc / pos2.size());
//		}

	}
	
	private String[] vcfFilenames = null;
	private List<String> annotationPrefix = null;
	
	private boolean onlyOutputPass = false;
	private boolean passRescue = false;
	private boolean writeLinkedEvents = false;
	private String eventKey = "EVENT";

	private int impreciseBuffer = 200;
	private int preciseBuffer = 10;
	private int minMatch = -1;

	// filename/vcfPrefix, map<eventID, sv-matches>
	private Map<String, Map<String, List<SVVCFCoord>>> events = new HashMap<String, Map<String, List<SVVCFCoord>>>();

    @Option(desc="Prefixes to use for overlapping VCF annotations (INFO/FORMAT; CSV or multiple allowed)", name="prefix", allowMultiple=true)
    public void setPrefix(String prefix) {
    	if (this.annotationPrefix == null) {
    		this.annotationPrefix = new ArrayList<String>();
    	}
    	
    	for (String s: prefix.split(",")) {
    		this.annotationPrefix.add(StringUtils.strip(s));
    	}
    }
    
    @Option(desc="SV Event INFO key name", name="event-key", defaultValue="EVENT")
    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }
    
    @Option(desc="If one member of an event matches, write out all members of the event", name="linked-events")
    public void setWriteLinkedEvents(boolean writeLinkedEvents) {
        this.writeLinkedEvents = writeLinkedEvents;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="If a matching SV passes in ANY input, reset the filter to PASS", name="pass-rescue")
    public void setPassRescue(boolean passRescue) {
        this.passRescue = passRescue;
    }
    
    @Option(desc="For PRECISE SVs, allow this much buffer (bp) for finding matches (default: 10)", name="precise-buf", defaultValue="10")
    public void setPreciseBuffer(int preciseBuffer) {
        this.preciseBuffer = preciseBuffer;
    }
    
    @Option(desc="For IMPRECISE SVs, allow this much buffer (bp) for finding matches (default: 200)", name="imprecise-buf", defaultValue="200")
    public void setImpreciseBuffer(int impreciseBuffer) {
        this.impreciseBuffer = impreciseBuffer;
    }
    
    @Option(desc="Minimum number of files in which an SV must be found in to be returned (default: all inputs)", name="min-match")
    public void setMinMatch(int minMatch) {
        this.minMatch = minMatch;
    }
    
    
    @UnnamedArg(name = "input1.vcf input2.vcf ... (add ,CT=CT to filename to set CT INFO key)", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	for (String s: filenames) {
    		if (s.equals("-")) {
        		throw new CommandArgumentException("VCFs must be saved as a file, not read from stdin.");
    		}
    	}
    	
    	if (annotationPrefix != null && annotationPrefix.size() != filenames.length) { 
    		throw new CommandArgumentException("You must have as many --prefix values as VCF files.");
    	}

    	this.vcfFilenames = filenames;

    }

	@Exec
	public void exec() throws Exception {
		if (minMatch < 1) {
			minMatch = vcfFilenames.length;
		}
		
		String firstVCF = null;

		
		Map<String, VCFHeader> headers = new HashMap<String, VCFHeader>();
		// chrom, list<sv-match>
		Map<String, List<SVVCFCoord>> cache = new HashMap<String, List<SVVCFCoord>>();
		
		
		for (int i=0; i< vcfFilenames.length; i++) {
			String fname = vcfFilenames[i];
			String ctName = null;
			if (vcfFilenames[i].contains(",")) {
				String[] spl = vcfFilenames[i].split(",");
				fname = spl[0];
				for (int j=1; j<spl.length; j++) {
					String[] spl1 = spl[j].split("=");
					if (spl1[0].equals("CT")) {
						ctName = spl1[1];
					}
				}
			}

			VCFReader reader = new VCFReader(fname);
//			System.err.println("VCF: " + fname);
			String vcfPrefix = "";
			
			if (this.annotationPrefix != null) {
				vcfPrefix = annotationPrefix.get(i);
			} else {
				vcfPrefix = "VCF" + (i + 1);
			}

			if (firstVCF == null) {
				firstVCF = vcfPrefix;
			}
			headers.put(vcfPrefix, reader.getHeader());
			
			events.put(vcfPrefix, new HashMap<String, List<SVVCFCoord>>());
			
			Iterator<VCFRecord> it = reader.iterator();

			for (VCFRecord rec: IterUtils.wrap(it)) {
//				if (onlyOutputPass && rec.isFiltered()) {
//					continue;
//				}

				if (!cache.containsKey(rec.getChrom())) {
					cache.put(rec.getChrom(), new ArrayList<SVVCFCoord>());
				}

				boolean isImprecise = false;
				if (rec.getInfo().contains("IMPRECISE")) {
					isImprecise = true;
				}
				
	            for (VCFAltPos alt: rec.getAltPos(null, null, null, ctName)) {
	            	boolean match = false;
	            	List<SVVCFCoord> matchingCoords = new ArrayList<SVVCFCoord>();
	            	
	            	if (alt.type == VCFVarType.SNV) {
	            		// this might work for consensus calling of SNVs... not tested.
	            		for (SVVCFCoord coord: cache.get(rec.getChrom())) {
	            			if (coord.type == alt.type) {
	            				if (coord.matchLeft(rec.getChrom(), rec.getPos(), false) && alt.alt.equals(coord.alt.alt)) {
	            					match = true;
	            					coord.addMatch(vcfPrefix, alt, rec, false, false);
	            					matchingCoords.add(coord);
	            				}
	            			}
	            		}
	            	} else if (alt.type == VCFVarType.INV) {
	            		// INV is handled specially!
	            		// convert INV to multiple BNDs and check...
	            		// Manta uses BNDs for INV

//	            		System.out.println("INV\t"+rec.getChrom()+":"+rec.getPos()+ " => " + alt.chrom+":"+alt.pos+"\t"+alt.type+"\t"+alt.connType+"\t"+isImprecise);
	            		
	            		for (SVVCFCoord coord: cache.get(rec.getChrom())) {
	            			if (coord.type == alt.type || coord.type == VCFVarType.BND && coord.ref2.equals(alt.chrom)) {
	            				// INV can match INV or BRD
	            			
	            				if (coord.matchLeft(rec.getChrom(), rec.getPos(), isImprecise) && coord.matchRight(alt.chrom, alt.pos, isImprecise)) {
//	        	            		System.out.println("Match!: " + rec.getChrom()+":"+ rec.getPos() + " => " + rec.getDbSNPID());
	            					match = true;
	            					coord.addMatch(vcfPrefix, alt, rec, isImprecise, true);
	            					matchingCoords.add(coord);
		            			}
	            			}
	            		}

	            		
	            	} else if (alt.type == VCFVarType.INS) {
	            		// INS is handled specially! (and might not work)	            		
	            		
	            		for (SVVCFCoord coord: cache.get(rec.getChrom())) {
	            			if (coord.conn != alt.connType || coord.type != alt.type || !coord.ref2.equals(alt.chrom)) {
	            				continue;
	            			}
	            			if (coord.matchLeft(rec.getChrom(), rec.getPos(), isImprecise)) {
	            				// match INS only on insertion coordinates -- inserted sequence could in in ALT, could be in an INFO tag, or might not be fully present at all (for large insertions)
//        	            		System.out.println("Match!: " + rec.getChrom()+":"+ rec.getPos() + " => " + rec.getDbSNPID());
            					match = true;
            					coord.addMatch(vcfPrefix, alt, rec, isImprecise, false);
            					matchingCoords.add(coord);
	            			}
	            		}
	            		
	            	} else if (alt.type != VCFVarType.UNK && alt.type != VCFVarType.CNV) {
	            		// DEL, DUP, BND
	            		for (SVVCFCoord coord: cache.get(rec.getChrom())) {
	            			if (coord.conn != alt.connType || coord.type != alt.type || !coord.ref2.equals(alt.chrom)) {
	            				continue;
	            			}
	            			
            				if (coord.matchLeft(rec.getChrom(), rec.getPos(), isImprecise) && coord.matchRight(alt.chrom, alt.pos, isImprecise)) {
//        	            		System.out.println("Match!: " + rec.getChrom()+":"+ rec.getPos() + " => " + rec.getDbSNPID());
            					match = true;
            					coord.addMatch(vcfPrefix, alt, rec, isImprecise, false);
            					matchingCoords.add(coord);
	            			}
	            		}
	            	} else {
	            		// not SNV, BND, DEP, DUP, INV, INS, so... let's skip it?
	            		continue;
	            	}
	            	if (!match) {
//	            		System.out.println("Adding: " + rec.getChrom()+":"+ rec.getPos() + " => " + rec.getDbSNPID());
	            		SVVCFCoord coord = new SVVCFCoord(rec.getChrom(), rec.getPos(), alt, isImprecise, vcfPrefix, rec, alt.type == VCFVarType.INV);
	            		cache.get(rec.getChrom()).add(coord);
    					matchingCoords.add(coord);
	            	}
	            	
	            	if (rec.getInfo().contains(eventKey) && rec.getInfo().get(eventKey) != null) {
	            		String event = rec.getInfo().get(eventKey).asString(null);
	            		if (!events.get(vcfPrefix).containsKey(event)) {
	            			events.get(vcfPrefix).put(event, new ArrayList<SVVCFCoord>());
	            		}
	            		events.get(vcfPrefix).get(event).addAll(matchingCoords);
	            	}
	            }
			}
			
			reader.close();
		}

		Set<SVVCFCoord> valid = new HashSet<SVVCFCoord>();

		for (String chrom: StringUtils.naturalSort(cache.keySet())) {
//			System.out.println("> " + chrom);
			for (SVVCFCoord coord: cache.get(chrom)) {
				if (coord.getCount() >= minMatch) {
//					if (coord.isInversion()) {
//						System.out.println("MATCH\t" + coord.ref1 + ":" + coord.getPos1() + "\t"+coord.ref2 + ":" + coord.getPos2() + "\t" + StringUtils.join(",", coord.matches) + "\t" + "INV" + "\t" + coord.conn + "\t" + coord.isImprecise());
//					} else {
//						System.out.println("MATCH\t" + coord.ref1 + ":" + coord.getPos1() + "\t"+coord.ref2 + ":" + coord.getPos2() + "\t" + StringUtils.join(",", coord.matches) + "\t" + coord.type + "\t" + coord.conn + "\t" + coord.isImprecise());
//					}

					// We do the --passing filter here, so that we can match all SVs.
					// If an SV fails in one VCF, but passes in another, we could potentially rescue it.
					
					boolean isPass = true;
					boolean anyPass = false;
					for (Pair<String, VCFRecord> pair: coord.records) {
						if (pair.two.isFiltered()) {
							isPass = false;
						} else {
							anyPass = true;
						}
					}
					
					if (this.onlyOutputPass && passRescue && anyPass) {
						// at least one input had this SV group as a PASS, so reset all 
						isPass = true;
						for (Pair<String, VCFRecord> pair: coord.records) {
							if (pair.two.isFiltered()) {
								pair.two.getInfo().put("ORIG_FILTER",new VCFAttributeValue(StringUtils.join(",",pair.two.getFilters())));
								pair.two.clearFilters();
								pair.two.getInfo().putFlag("CG_PASS_RESCUE");
							}
						}
					}
					
					if (this.onlyOutputPass && !isPass) {
						continue;
					}

					valid.add(coord);

					// also write all events linked to this svvcfcoord
					if (writeLinkedEvents) {
						for (Pair<String, VCFRecord> pair: coord.records) {
							String vcfPrefix = pair.one;
							VCFRecord rec = pair.two;
							if (rec.getInfo().contains(eventKey) && rec.getInfo().get(eventKey) != null) {
			            		String event = rec.getInfo().get(eventKey).asString(null);
			            		if (events.get(vcfPrefix).containsKey(event) && events.get(vcfPrefix).get(event) != null) {
			            			for (SVVCFCoord coord2: events.get(vcfPrefix).get(event)) {
//			    						System.out.println("LINKED\t" + coord2.ref1 + ":" + coord2.getPos1() + "\t"+coord2.ref2 + ":" + coord2.getPos2() + "\t" + StringUtils.join(",", coord2.matches) + "\t" + coord2.type + "\t" + coord2.conn + "\t" + coord2.isImprecise());
			    						
			            				boolean isPass2 = true;
			            				for (Pair<String, VCFRecord> pair2: coord2.records) {
			    							if (pair2.two.isFiltered()) {
			    								if (isPass && passRescue) {
				    								// at least one input had this SV EVENT as a PASS, so reset all 

			    									pair2.two.getInfo().put("ORIG_FILTER",new VCFAttributeValue(StringUtils.join(",",pair2.two.getFilters())));

			    									pair2.two.clearFilters();
				    								pair2.two.getInfo().putFlag("CG_EVENT_RESCUE");
			    								} else {
			    									isPass2 = false;
			    								}
			    							}
			    						}

			            				if (this.onlyOutputPass && !isPass2) {
			            					continue;
			            				}
		            					valid.add(coord2);
			            			}
			            		}
							}
						}
					}
				}
			}
		}
		
		VCFHeader header = buildNewHeader(headers, firstVCF);
		writeVCF(header, valid);
	}
	
	private void writeVCF(VCFHeader header, Collection<SVVCFCoord> coords) throws IOException, VCFAttributeException {
		
		Map<String, List<VCFRecord>> chromRecords = new HashMap<String, List<VCFRecord>>();
		
		for (SVVCFCoord coord: coords) {
			String chrom = coord.records.get(0).two.getChrom();
			if (!chromRecords.containsKey(chrom)) {
				chromRecords.put(chrom, new ArrayList<VCFRecord>());
			}
			chromRecords.get(chrom).add(mergeVCFRecord(coord, header));
		}

		VCFWriter writer = new VCFWriter(out, header);
		for (String chrom: StringUtils.naturalSort(chromRecords.keySet())) {
			Collections.sort(chromRecords.get(chrom), new Comparator<VCFRecord>() {
				@Override
				public int compare(VCFRecord rec1, VCFRecord rec2) {
					return rec1.getPos() - rec2.getPos();
				}});
			for (VCFRecord rec: chromRecords.get(chrom)) {
				writer.write(rec);
			}
		}
	}

	private VCFRecord mergeVCFRecord(SVVCFCoord coord, VCFHeader header) throws VCFAttributeException {
		// use the first VCF file as the position of record... 
		VCFRecord record = new VCFRecord(coord.ref1, coord.records.get(0).two.getPos(), coord.records.get(0).two.getRef());
		VCFAttributes info = new VCFAttributes();
		List<VCFAttributes> format = new ArrayList<VCFAttributes>();
		
		for (int i=0; i<header.getSamples().size(); i++) {
			format.add(new VCFAttributes());
		}
		
		boolean eventInversion = coord.isInversion();
		for (Pair<String, VCFRecord> pair: coord.records) {
			if (eventInversion) {
				break;
			}
			if (pair.two.getInfo().contains(eventKey) && pair.two.getInfo().get(eventKey) != null) {
        		String eventName = pair.two.getInfo().get(eventKey).asString(null);
        		
        		for (SVVCFCoord c2: events.get(pair.one).get(eventName)) {
        			if (c2.isInversion()) {
        				eventInversion = true;
        				break;
        			}
        		}
			}
		}
		
		
		
		double qual = -1;
		String alt = "";
		// Alt will be fixed.
		if (header.getAlts().contains("INV") && eventInversion) { 
			alt = "<INV>";
			info.put("END", new VCFAttributeValue(""+coord.records.get(0).two.getAltPos().get(0).pos));
			info.put("SVTYPE", new VCFAttributeValue("INV"));
			info.put("CT", new VCFAttributeValue(coord.conn.toString()));
		} else 	if (coord.type == VCFVarType.DEL) {
			// if ref value is > 1, then use the VCFRecord.alt value here.
			// this could loose data if the 'alt' value has more than one value
			if (coord.records.get(0).two.getRef().length()>1) {
				alt = coord.records.get(0).two.getAlt().get(0);
			} else {
				alt = "<DEL>";
			}
			info.put("END", new VCFAttributeValue(""+coord.records.get(0).two.getAltPos().get(0).pos));
			info.put("SVTYPE", new VCFAttributeValue("DEL"));
		} else if (coord.type == VCFVarType.DUP) {
			if (header.getAlts().contains("DUP:TANDEM")) {
				alt = "<DUP:TANDEM>";
			} else {
				alt = "<DUP>";
			}
			info.put("END", new VCFAttributeValue(""+coord.records.get(0).two.getAltPos().get(0).pos));
			info.put("SVTYPE", new VCFAttributeValue("DUP"));
		} else if (coord.type == VCFVarType.INS) {
			alt = "<INS>";
			info.put("SVTYPE", new VCFAttributeValue("INS"));

			List<Integer> inslen = new ArrayList<Integer>();
			for (Pair<String, VCFRecord> pair: coord.records) {
				if (pair.two.getInfo().contains("INSLEN")) {
					inslen.add(pair.two.getInfo().get("INSLEN").asInt());
				} else if (pair.two.getInfo().contains("SVLEN")) {
					inslen.add(pair.two.getInfo().get("SVLEN").asInt());
				}
			}

			if (inslen.size()>0) {
				int val = inslen.get(0);
				for (int i: inslen) {
					if (i < val) {
						val = i;
					}
				}
				
				info.put("INSLEN", new VCFAttributeValue(""+val));
			}
			
			
		} else if (coord.type == VCFVarType.BND && 
				(coord.conn == VCFSVConnection.FiveToFive || 
				coord.conn == VCFSVConnection.ThreeToFive ||
				coord.conn == VCFSVConnection.ThreeToThree ||
				coord.conn == VCFSVConnection.FiveToThree
				)) {
			
			// BND, but of a type we know. If this is an NtoN (INS), NA (SNV), or UNK (INV, but should be pulled from CT), then we can't write it.
			
			info.put("END", new VCFAttributeValue(""+coord.records.get(0).two.getAltPos().get(0).pos));
			info.put("SVTYPE", new VCFAttributeValue("BND"));
			info.put("CHR2", new VCFAttributeValue(coord.ref2));
			info.put("CT", new VCFAttributeValue(coord.conn.toString()));

//			String altChr = coord.ref2;
//			int altPos = coord.getPos2();
//			String refBase = coord.records.get(0).two.getRef();
//
//			if (coord.conn == VCFSVConnection.FiveToFive) {
//				alt =  "[" + altChr + ":" + altPos + "[" + refBase;
//			} else if (coord.conn == VCFSVConnection.ThreeToFive) {
//				alt = refBase + "[" + altChr + ":" + altPos + "[";
//			} else if (coord.conn == VCFSVConnection.ThreeToThree) {
//				alt = refBase + "]" + altChr + ":" + altPos + "]";
//			} else if (coord.conn == VCFSVConnection.FiveToThree) {
//				alt =  "]" + altChr + ":" + altPos + "]" + refBase;
//			}

			// Don't try to rewrite the BND code (see above), just use the first VCF file's ALT.
			//coord.records.get(0).two.getAltPos().get(0).pos;
			alt = coord.records.get(0).two.getAltOrig();
			//alt = alt + "," + coord.records.get(0).two.getAltOrig();
			
		} else {
			// otherwise, just use the first value.
			// not an SV (should only be SNV here)
			alt = coord.records.get(0).two.getAltOrig();
		}
		
		if (info.contains("SVTYPE")) {
			// this is an SV, so set PRECISE/IMPRECISE
			if (coord.isImprecise()) {
				info.putFlag("IMPRECISE");
			} else {
				info.putFlag("PRECISE");
			}
		}
		
		info.put("CG_VCF_MATCHES", new VCFAttributeValue(StringUtils.join(",", coord.matches)));

		// set IDs
		List<String> dbIDs = new ArrayList<String>();
		List<String> filters = new ArrayList<String>();
		
		List<String> events = new ArrayList<String>();
		
		for (Pair<String, VCFRecord> pair: coord.records) {
			dbIDs.add(pair.one+"_"+pair.two.getDbSNPID());
			
			// report the lowest qual score *if* it is present
			if (pair.two.getQual() > -1) {
				if (qual == -1) {
					qual = pair.two.getQual();
				} else if (pair.two.getQual() < qual) {
					qual = pair.two.getQual();					
				}
			}
			
			if (pair.two.isFiltered()) {
				// This must not be PASS or '.'
				for (String filter: pair.two.getFilters()) {
					filters.add(pair.one+"_"+filter);
				}
				
				info.put(pair.one+"_FILTER",new VCFAttributeValue(StringUtils.join(",",pair.two.getFilters())));
				
			}
			for (String infoKey: pair.two.getInfo().getKeys()) {
				if (infoKey.equals("CG_PASS_RESCUE") || infoKey.equals("CG_EVENT_RESCUE")) {
					// just pass these values along.
					info.put(infoKey, pair.two.getInfo().get(infoKey));
				} else if (infoKey.equals("EVENT")) {
					events.add(pair.two.getInfo().get(infoKey).asString(null));
					info.put(pair.one+"_"+infoKey, pair.two.getInfo().get(infoKey));
				} else {
					info.put(pair.one+"_"+infoKey, pair.two.getInfo().get(infoKey));
				}
			}
			
			for (String sample: header.getSamples()) {
				int idx = coord.records.get(0).two.getParentHeader().getSamplePosByName(sample);
				for (String formatKey: pair.two.getFormatBySample(sample).getKeys()) {
					format.get(idx).put(pair.one+"_"+formatKey, pair.two.getFormatBySample(sample).get(formatKey));
				}
			}
		}
		
		if (events.size() > 0) {
			info.put("EVENT", new VCFAttributeValue(StringUtils.join(",", events)));
		}
		
		record.setQual((int)Math.floor(qual));
		
		record.addAlt(alt);
		record.setDbSNPID(StringUtils.join(",", dbIDs));
		for (String filter: filters) {
			record.addFilter(filter);
		}
		record.setInfo(info);
		for (VCFAttributes fmt: format) {
			record.addSampleAttributes(fmt);
		}
		return record;
	}

	private VCFHeader buildNewHeader(Map<String, VCFHeader> headers, String firstVCFName) throws IOException, VCFParseException {
		VCFHeader newHeader = new VCFHeader();

		// We will use these info values as base-level names. All other INFO fields will be prefixed with the VCFprefix ex: VCF1_key
		// first three are from the spec...  
		newHeader.addInfo(VCFAnnotationDef.info("END", "1", "Integer", "END position for the SV"));
		newHeader.addInfo(VCFAnnotationDef.info("IMPRECISE", "0", "Flag", "SV pos is IMPRECISE"));
		newHeader.addInfo(VCFAnnotationDef.info("SVTYPE", "1", "Integer", "Type of SV"));

		newHeader.addInfo(VCFAnnotationDef.info("CG_VCF_MATCHES", ".", "String", "Input VCF files that have matches for this SV"));
		newHeader.addInfo(VCFAnnotationDef.info("CHR2", "1", "String", "Chromosome for the END value for BND SVs"));
		newHeader.addInfo(VCFAnnotationDef.info("PRECISE", "0", "Flag", "SV pos is PRECISE"));
		newHeader.addInfo(VCFAnnotationDef.info("CT", "1", "String", "SV connection type"));
		newHeader.addInfo(VCFAnnotationDef.info("INSLEN", "1", "Integer", "Length of insertion (INS only, min of input estimates)"));

		newHeader.addInfo(VCFAnnotationDef.info("EVENT", "1", "String", "IDs of event associated to breakend"));
		newHeader.addInfo(VCFAnnotationDef.info("CG_PASS_RESCUE", "0", "Flag", "SV was filtered by at least one input VCF"));
		newHeader.addInfo(VCFAnnotationDef.info("CG_EVENT_RESCUE", "0", "Flag", "SV was filtered but is linked to an EVENT with at least one passing member"));

		
		// Only PASS is strictly defined. all other filter values will be prefixed
		newHeader.addFilter(VCFFilterDef.build("PASS", "All filters passed"));

		// All format values will be prefixed -- including GT if present.
		// rename all INFO values
		for (String vcf: headers.keySet()) {
			for (String infoName: headers.get(vcf).getInfoIDs()) {
				VCFAnnotationDef def = headers.get(vcf).getInfoDef(infoName);
				newHeader.addInfo(def.copy(vcf+"_"+def.id));
			}
		}		

		// Add VCF_FILTER value
		for (String vcf: headers.keySet()) {
			newHeader.addInfo(VCFAnnotationDef.info(vcf+"_FILTER", ".", "String", "Filter values from the "+vcf+" input"));
			newHeader.addInfo(VCFAnnotationDef.info(vcf+"_ORIG_FILTER", ".", "String", "Original filter values from the "+vcf+" input (before rescue)"));
		}		

		
		// rename all FORMAT values
		for (String vcf: headers.keySet()) {
			for (String formatName: headers.get(vcf).getFormatIDs()) {
				VCFAnnotationDef def = headers.get(vcf).getFormatDef(formatName);
				newHeader.addFormat(def.copy(vcf+"_"+def.id));
			}
		}
		

		// take contigs from the first VCF only
		VCFHeader first = headers.get(firstVCFName);
		for (String contig: first.getContigNames()) {
			newHeader.addContig(VCFContigDef.build(contig, first.getContigLength(contig)));
		}
		
		// Merge ALTs from all VCFs (keep track if "INV" is used -- if so, we will use INV for all Inversions)
		// if DUP:TANDEM present, prefer it over "DUP"
		
		Map<String, VCFAltDef> alts = new HashMap<String,VCFAltDef>();
		for (String vcf: headers.keySet()) {
			VCFHeader h = headers.get(vcf);
			for (String id: h.getAlts()) {
				VCFAltDef alt = h.getAlt(id);
				alts.put(alt.id, alt);
			}
		}
		
		if (alts.containsKey("DUP") && alts.containsKey("DUP:TANDEM")) {
			alts.remove("DUP");
		}
		

		for (String id: alts.keySet()) {
			newHeader.addAlt(alts.get(id));
		}
		
		
		// Add some other fixed values...
		
	
		IterUtils.zip(headers.keySet(), ListBuilder.build(vcfFilenames), new EachPair<String, String>() {

			@Override
			public void each(String foo, String bar) {
				newHeader.addLine("##"+foo+"_inputVCFfilename="+bar);
				
			}});
		
		newHeader.addLine("##ngsutilsj_vcf_svconsensusCommand="+NGSUtils.getArgs());
		newHeader.addLine("##ngsutilsj_vcf_svconsensusVersion="+NGSUtils.getVersion());

		
		// add all other lines...
		for (String vcf: headers.keySet()) {
			for (String line: headers.get(vcf).getLines()) {
				while (line.startsWith("#")) {
					line = line.substring(1);
				}
				line = "##" + vcf + "_" + line;
				newHeader.addLine(line);
			}
		}
		
		// SAMPLES in same order as the first file
		
		for (String sample: headers.get(firstVCFName).getSamples()) {
			newHeader.addSample(sample);
		}
		
		return newHeader;
		
	}
}
