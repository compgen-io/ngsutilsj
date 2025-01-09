package io.compgen.ngsutils.cli.vcf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.support.GlobUtils;
import io.compgen.ngsutils.vcf.VCFAttributeException;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFParseException;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-sample-export", desc="Write sample FORMAT values to a tab-delimited file, with one sample per line", category="vcf")
public class VCFSampleExport extends AbstractOutputCommand {
	private String filename = "-";
	private List<String> validKeys = null;
	private Set<String> validSamples = null;
	private boolean convertGT = false;
	private boolean passing = false;
//	private boolean onlySNV = false;
	private boolean exportID = false;
	private static boolean quiet = false;
	
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }
    @Option(desc="Quiet output (no progress bar)", name="quiet", charName="q")
    public void setQuiet(boolean quiet) {
    	VCFSampleExport.quiet = quiet;
    }

    @Option(desc="Export this sample (can be glob, multiple allowed)", name="sample", charName="s", allowMultiple=true)
    public void setSample(String sample) {
    	if (validSamples == null) {
    		validSamples = new HashSet<String>();
    	}
    	validSamples.add(sample);
    }
    
    @Option(desc="Export this key (can be glob, multiple allowed, at least one required)", name="key", charName="k", allowMultiple=true)
    public void setKey(String key) {
    	if (validKeys == null) {
    		validKeys = new ArrayList<String>();
    	}
    	validKeys.add(key);
    }

    @Option(desc="Export and convert genotype value to ACGT (0/1 and 1/0 will be written as ref/alt)", name="gt")
    public void setGT(boolean val) {
    	if (val) {
			setKey("GT");
			convertGT=true;
    	}
    }

    @Option(desc="Export ID field", name="id")
    public void setExportId(boolean val) {
    	exportID = val;
    }

    @Option(desc="Write variants passing filters only", name="passing")
    public void setPassing(boolean val) {
    	passing = val;
    }

//
//    @Option(desc="Only write SNVs", name="snvs")
//    public void setOnlySNV(boolean val) {
//    	onlySNV = val;
//    }

	@Exec
	public void exec() throws CommandArgumentException, VCFAttributeException, IOException, VCFParseException {
		if (validKeys == null) {
			throw new CommandArgumentException("You must specify at least one field to export");
		}
		
		VCFReader reader = new VCFReader(filename);

		Iterator<VCFRecord> it = null;
		if (VCFSampleExport.isQuiet()) {
			it = reader.iterator();
		} else {
	        it  = ProgressUtils.getIterator(reader.getFilename(), 
	        		reader.iterator(), 
	        		(reader.getChannel() == null)? null : new FileChannelStats(reader.getChannel()), 
					new ProgressMessage<VCFRecord>() {
			            public String msg(VCFRecord current) {
			                return current.getChrom()+":"+current.getPos();
			            }}, 
					new CloseableFinalizer<VCFRecord>());
		}

		List<String> samples = new ArrayList<String>();
		List<String> keys = new ArrayList<String>();
		
		for (String s : reader.getHeader().getSamples()) {
			if (validSamples == null || validSamples.contains(s)) {
				samples.add(s);
				continue;
			}
			for (String g: validSamples) {
	            if (GlobUtils.matches(s, g)) {
					samples.add(s);
	            }
			}
		}

		for (String key : reader.getHeader().getFormatIDs()) {
			if (validKeys.contains(key)) {
				keys.add(key);
				continue;
			}
			for (String g: validKeys) {
	            if (GlobUtils.matches(key, g)) {
	            	if (!keys.contains(key)) {
	            		keys.add(key);
	            	}
	            }
			}
		}

		TabWriter tab = new TabWriter(out);
		tab.write("chrom");
		tab.write("pos");
		if (exportID) {
			 tab.write("ID");
		}
		tab.write("ref");
		tab.write("alt");
		tab.write("sample");
		for (String k:keys) {
			tab.write(k);
		}
		tab.eol();
		
		while (it.hasNext()) {
			VCFRecord rec = it.next();
			if (passing && rec.isFiltered()) {
				continue;
			}
			
			for (String s: samples) {
//				System.err.println(s);
			
				tab.write(rec.getChrom());
				tab.write(rec.getPos());
				if (exportID) {
					tab.write(rec.getDbSNPID());
				}
				tab.write(rec.getRef());
				tab.write(rec.getAltOrig());
				tab.write(s);
				
				
				VCFAttributes attr = rec.getFormatBySample(s);
				for (String k: keys) {
					String val = attr.get(k).toString();
					if (k.equals("GT") && convertGT) {
						// extract the GT value and convert it to basecalls
//						System.err.println(k + " => " + val);
						String[] spl = null;
						if (val.contains("/")) {
							spl = val.split("/");							
						} else if (val.contains("|")) {
							spl = val.split("\\|");							
						} else {
							tab.write(".");
							continue;
						}
//						System.err.println("spl.length="+spl.length+" " +StringUtils.join(",", spl));

						if (spl.length != 2) {
							tab.write(".");								
							continue;
						}
						if (spl[0].equals(".") || spl[1].equals(".")) {
							tab.write(".");								
							continue;
						}
						String gt = "";
//						System.err.println(val);
						if (spl[0].equals("0")) {
							if (spl[1].equals("0")) {
								gt = rec.getRef()+"/" + rec.getRef();
							} else {
								gt = rec.getRef()+"/" + rec.getAlt().get(Integer.parseInt(spl[1])-1);
							}
						} else if (spl[1].equals("0")) {
							gt = rec.getRef()+"/" + rec.getAlt().get(Integer.parseInt(spl[0])-1);
						} else {
							gt = rec.getAlt().get(Integer.parseInt(spl[0])-1)+"/" + rec.getAlt().get(Integer.parseInt(spl[1])-1);
						}
						tab.write(gt);
					} else { 
						tab.write(val);
					}
				}
				
				tab.eol();
			}
		}
		
		tab.close();
		reader.close();
	}
	public static boolean isQuiet() {
		return quiet;
	}
}