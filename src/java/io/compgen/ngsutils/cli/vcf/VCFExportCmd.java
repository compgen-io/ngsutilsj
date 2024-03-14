package io.compgen.ngsutils.cli.vcf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.Sorter;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.export.ExportFormatField;
import io.compgen.ngsutils.vcf.export.ExportInfoField;
import io.compgen.ngsutils.vcf.export.FilterExport;
import io.compgen.ngsutils.vcf.export.IDExport;
import io.compgen.ngsutils.vcf.export.QualExport;
import io.compgen.ngsutils.vcf.export.VCFExport;


@Command(name="vcf-export", desc="Export information from a VCF file as a tab-delimited file", category="vcf", doc="For --format, possible allele values are: sum, min, max, ref, or alt1 (the first alt allele). The value may also be left blank.")
public class VCFExportCmd extends AbstractOutputCommand {
	private String filename = "-";
	
	List<VCFExport> chain = new ArrayList<VCFExport>();
	
	Map<String, String> extras = new LinkedHashMap<String,String>();
	
    private boolean noHeader = false;
    private boolean uniqueEvent = false;
    private boolean noVCFHeader = false;
    private boolean onlyOutputPass = false;
    private boolean onlySNVs = false;
    private boolean onlyIndel = false;
	private boolean missingBlank = false;
	
	private String[] eventSort = new String[0];
	private Map<String, Map<Integer, List<String>>> outputBuffer = new HashMap<String, Map<Integer, List<String>>>();
	private Map<String, List<Pair<VCFRecord, List<String>>>> eventBuffer = new HashMap<String, List<Pair<VCFRecord, List<String>>>>();
	
	
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
    	this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Set missing values to be \"\".", name="missing-blank")
    public void setMissingBlank(boolean missingBlank) {
        this.missingBlank = missingBlank;
    }
    
    @Option(desc="Only export SNVs", name="only-snvs")
    public void setOnlySNV(boolean onlySNV) {
        this.onlySNVs = onlySNV;
    }

    @Option(desc="Only export one record for an SV event (requires EVENT INFO, can require lot of memory!)", name="unique-event")
    public void setUniqueEvent(boolean uniqueEvent) {
        this.uniqueEvent = uniqueEvent;
    }

    @Option(desc="When using event-sort, sort records by these INFO annotations (default: write the first record)", name="unique-event-sort")
    public void setUniqueEventSort(String eventSort) {
        this.eventSort = eventSort.split(",");
        uniqueEvent = true;
    }

    @Option(desc="Only export Indels", name="only-indels")
    public void setOnlyIndel(boolean onlyIndel) {
        this.onlyIndel = onlyIndel;
    }

    @Option(desc="Don't export the header line", name="no-header")
    public void setNoHeader(boolean noHeader) {
        this.noHeader = noHeader;
    }

    @Option(desc="Don't export the VCF header", name="no-vcf-header")
    public void setNoVCFHeader(boolean noVCFHeader) {
        this.noVCFHeader = noVCFHeader;
    }

    @Option(desc="Export VCF Filters", name="filter")
    public void setFilter() throws CommandArgumentException {
        chain.add(new FilterExport());
    }
    
    @Option(desc="Export VCF ID", name="id")
    public void setID() throws CommandArgumentException {
        chain.add(new IDExport());
    }
    
    @Option(desc="Export VCF Qual", name="qual")
    public void setQual() throws CommandArgumentException {
        chain.add(new QualExport());
    }
    
    @Option(desc="Add a column to the beginning of the line", name="col", helpValue="{name:}value", allowMultiple=true)
    public void setCol(String val) throws CommandArgumentException {
        if (val.indexOf(":") > -1) {
            String[] ar = val.split(":");
            extras.put(ar[0], ar[1]);
        } else {
            extras.put("col"+(extras.size()+1), val);
        }
    }
    
    @Option(desc="Export FORMAT field", name="format", helpValue="ID{:SAMPLE:ALLELE:NEWSAMPLE}", allowMultiple=true)
    public void setFormat(String vals) throws CommandArgumentException {
    	for (String val: vals.split(",")) {
	        boolean ignoreMissing = true;
	        
	        if (val.endsWith(":!")) {
	            ignoreMissing = false;
	            val = val.substring(0,  val.length()-2);
	        } else if (val.endsWith(":?")) {
	            ignoreMissing = true;
	            val = val.substring(0,  val.length()-2);
	        }
	        
	        String key=null;
	        String sample=null;
	        String allele=null;
	        String newName=null;
	        
	        
	        String[] spl = val.split(":");
	        
	        for (String s: spl) {
	            if (key == null) {
	                key = s;
	            } else if (sample == null) {
	                sample = s;
	            } else if (allele == null) {
	                allele = s;
	            } else if (newName == null) {
	                newName = s;
	            }
	        }
	
	        if (key == null || key.equals("")) {
	            throw new CommandArgumentException("Missing argument for --format!");
	        }
	        if (sample != null && sample.equals("")) {
	            sample = null;
	        }
	        if (allele != null && allele.equals("")) {
	            allele = null;
	        }
	        if (newName != null && newName.equals("")) {
	            newName = null;
	        }
	
	        if (newName != null && sample == null) {
	            throw new CommandArgumentException("Invalid argument for --format! You must specify a SAMPLE is ALIAS is given.");
	        }
	        
	        chain.add(new ExportFormatField(key, sample, allele, ignoreMissing, newName));
    	}
    }
        
    @Option(desc="Export INFO field", name="info", helpValue="KEY{:ALLELE}", allowMultiple=true)
    public void setInfo(String vals) throws CommandArgumentException {
    	for (String val: vals.split(",")) {
	    	boolean ignoreMissing = true;
	        if (val.endsWith(":!")) {
	            ignoreMissing = false;
	            val = val.substring(0,  val.length()-2);
	        } else if (val.endsWith(":?")) {
	            ignoreMissing = true;
	            val = val.substring(0,  val.length()-2);
	        }
	
	        String[] spl = val.split(":");
	    	if (spl.length == 1) {
	    		chain.add(new ExportInfoField(spl[0], null, ignoreMissing));
	    	} else {
	    		chain.add(new ExportInfoField(spl[0], spl[1], ignoreMissing));
	    	}
    	}
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {
        if (onlySNVs && onlyIndel) {
            throw new CommandArgumentException("You can't set both --only-snvs and --only-indels at the same time!");
        }

        VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}

        TabWriter writer = new TabWriter();

        VCFHeader header = reader.getHeader();
        if (uniqueEvent) {
        	if (header.getInfoDef("EVENT") == null) {
	            throw new CommandArgumentException("--unique-event is only valid for VCFs with EVENT INFO annotations!");
        	}
        }
        
        for (VCFExport export: chain) {
            export.setHeader(header);
            if (missingBlank) {
                export.setMissingValue("");
            }
        }

        if (!noVCFHeader) {
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		header.write(baos, false);
    		baos.close();
    		String headerString = baos.toString();
    		for (String s: headerString.split("\n")) {
    			writer.write_line(s);
    		}
    		
            writer.write_line("##ngsutilsj_vcf_exportCommand="+NGSUtils.getArgs());
            writer.write_line("##ngsutilsj_vcf_exportVersion="+NGSUtils.getVersion());
		}

        if (!noHeader) {
            // write the column names
            for (String k: extras.keySet()) {
                writer.write(k);
            }
            
            writer.write("chrom", "pos", "ref", "alt");
    		for (VCFExport export: chain) {
    			writer.write(export.getFieldNames());
    		}
    		writer.eol();
        }

        Iterator<VCFRecord> it = reader.iterator();
//        Set<String> events = new HashSet<String>();

		for (VCFRecord rec: IterUtils.wrap(it)) {
			if (onlyOutputPass && rec.isFiltered()) {
				continue;
			}
			
            if (onlySNVs && rec.isIndel()) {
                continue;               
            }

            if (onlyIndel && !rec.isIndel()) {
                continue;          
            }

//            if (uniqueEvent) {
//            	if (rec.getInfo().contains("EVENT")) {
//            		String event = rec.getInfo().get("EVENT").asString(null);
//            		if (event != null && !event.equals("")) {
//            			if (events.contains(event)) {
//            				continue;
//            			} else {
//            				events.add(event);
//            			}
//            		}
//            	}
//            }

            
			List<String> outs = new ArrayList<String>();

	        for (String k: extras.keySet()) {
	            outs.add(extras.get(k));
	        }
			
			outs.add(rec.getChrom());
			outs.add(""+rec.getPos());
			outs.add(rec.getRef());
			outs.add(StringUtils.join(",", rec.getAlt()));
			
			for (VCFExport export: chain) {
				export.export(rec, outs);
			}

            if (uniqueEvent) {
            	if (rec.getInfo().contains("EVENT")) {
            		String event = rec.getInfo().get("EVENT").asString(null);
            		if (event != null && !event.equals("")) {
                    	pushEventRecord(event, rec, outs);
                    	continue;
            		}
            	}
            	pushRecord(rec.getChrom(), rec.getPos(), outs);
            } else {
            	writer.write(outs);
            	writer.eol();
            }
		}
		
        if (uniqueEvent) {
			findBestEvents();
			writeRecords(writer);
        }
        
		reader.close();
		writer.close();
	}

	private void writeRecords(TabWriter writer) throws IOException {
		for (String chrom: StringUtils.naturalSort(this.outputBuffer.keySet())) {
			List<Integer> tmp1 = new ArrayList<Integer>();
			for (int pos:this.outputBuffer.get(chrom).keySet()) {
				tmp1.add(pos);
			}
			Collections.sort(tmp1);
			for (int pos: tmp1) {
				writer.write(this.outputBuffer.get(chrom).get(pos));
				writer.eol();
			}
		}
	}
	
	private void pushRecord(String chrom, int pos, List<String>outs) {
		if (!this.outputBuffer.containsKey(chrom)) {
			this.outputBuffer.put(chrom, new HashMap<Integer, List<String>>());
		}
		this.outputBuffer.get(chrom).put(pos,  outs);
	}

	private void pushEventRecord(String event, VCFRecord rec, List<String>outs) {
		if (!this.eventBuffer.containsKey(event)) {
			this.eventBuffer.put(event, new ArrayList<Pair<VCFRecord, List<String>>>());
		}
		this.eventBuffer.get(event).add(new Pair<VCFRecord, List<String>>(rec, outs));
	}

	private void findBestEvents() throws VCFAttributeException {
		
		String keyType = null;
		
		for (String event: this.eventBuffer.keySet()) {
			if (keyType == null) {
				keyType = "";
				VCFHeader header = this.eventBuffer.get(event).get(0).one.getParentHeader();
				if (this.eventSort != null) {
					for (String infoKey: this.eventSort) {
						boolean desc = false;
						if (infoKey.charAt(0) == '-') {
							desc = true;
							infoKey = infoKey.substring(1);
						} else if (infoKey.startsWith("\\-")) {
							desc = true;
							infoKey = infoKey.substring(2);
						}
						//Integer", "Float", "Flag", "Character", "String
						if (!header.getInfoIDs().contains(infoKey)) {
							throw new VCFAttributeException("Missing INFO: "+infoKey);
						}
						String type = header.getInfoDef(infoKey).type;
						if (type.equals("Integer")) {
							if (desc) {
								keyType += "I";
							} else {
								keyType += "i";
							}
						} else if (type.equals("Float")) {
							if (desc) {
								keyType += "F";
							} else {
								keyType += "f";
							}
						} else {
							if (desc) {
								keyType += "S";
							} else {
								keyType += "s";
							}
						}
					}
				}
			}
			String[][] tmp = new String[this.eventBuffer.get(event).size()][this.eventSort.length+2];
			for (int i = 0; i<this.eventBuffer.get(event).size(); i++) {
				Pair<VCFRecord, List<String>> pair = this.eventBuffer.get(event).get(i);
				String[] infoVals = extractInfoValues(pair.one, this.eventSort);
				for (int j=0; j<infoVals.length; j++) {
					tmp[i][j] = infoVals[j];
				}
				tmp[i][infoVals.length] = pair.one.getChrom();
				tmp[i][infoVals.length+1] = ""+pair.one.getPos();
			}

//			System.out.println("Before sort " + keyType+"ni");
//			for (String[] t: tmp) {
//				System.out.println(StringUtils.join(";", t));
//			}
			
			try {
				Sorter.sortValues(tmp, keyType+"  "); // the spaces are important
			} catch (Exception e) {
				throw new VCFAttributeException(e);
			}

//			System.out.println("After sort");
//			for (String[] t: tmp) {
//				System.out.println(StringUtils.join(";", t));
//			}

			String bestChr = tmp[0][this.eventSort.length];
			int bestPos = Integer.parseInt(tmp[0][this.eventSort.length+1]);
			for (Pair<VCFRecord, List<String>> pair:this.eventBuffer.get(event)) {
				if (pair.one.getChrom().equals(bestChr) && pair.one.getPos() == bestPos) {
//					System.out.println("Pushing record: " + bestChr+":"+bestPos+" => "+StringUtils.join(",", pair.two));
					pushRecord(bestChr, bestPos, pair.two);
					break;
				}
			}
		}
	}

	private String[] extractInfoValues(VCFRecord one, String[] keys) throws VCFAttributeException {
		String[] ret = new String[keys.length];
		for (int i=0; i< keys.length; i++) {
//			System.out.println("Looking for: "+keys[i]+ " in " + StringUtils.join(",", one.getInfo().getKeys()));
			if (one.getInfo().contains(keys[i])) {
				if (one.getParentHeader().getInfoDef(keys[i]).type.equals("Flag")) {
					ret[i] = keys[i];
				} else {
					ret[i] = one.getInfo().get(keys[i]).asString(null);
				}
			} else {
				ret[i] = "";
			}
		}
		return ret;
	}
}
