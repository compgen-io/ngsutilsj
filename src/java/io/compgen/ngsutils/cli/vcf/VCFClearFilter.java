package io.compgen.ngsutils.cli.vcf;

import java.nio.channels.FileChannel;
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
import io.compgen.common.IterUtils;
import io.compgen.common.TallyValues;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFAttributeValue;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-clearfilter", desc="Remove a filter from a VCF file", category="vcf", doc=
          "This will remove previously set filters from individual variants.\n"
		+ "By default this will remove the filter from all variants, but it can\n"
		+ "also be used to clear filters when they are the *only* filter set\n"
		+ "for a variant. Cleared filters will be annotated in the CG_CLEARED_FILTER\n"
		+ "INFO field.")
public class VCFClearFilter extends AbstractOutputCommand {
	private String filename = "-";
	
    private boolean onlyOutputPass = false;
    private boolean clearOnly = false;
	
    private Set<String> clearFilters = new HashSet<String>();
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Only clear a filter if this/these are the only filters (you can specify multiple filters to clear)", name="only")
    public void setClearSoloOnly(boolean clearOnly) {
        this.clearOnly = clearOnly;
    }
    
    @Option(desc="Clear this filter (multiple allowed)", name="filter", helpValue="val", allowMultiple=true)
    public void addFilterClear(String val) throws CommandArgumentException {
    	for (String v: val.split(",")) {
    		clearFilters.add(v);
    	}
    }

    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
		VCFHeader header = reader.getHeader();
		
		if (!header.getInfoIDs().contains("CG_CLEARED_FILTER")) {
			header.addInfo(VCFAnnotationDef.info("CG_CLEARED_FILTER", ".", "String", "Filters that have been removed from this variant", null, null, null, null));

		}
		
		header.addLine("##ngsutilsj_vcf_clearfilterCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_clearfilterVersion="+NGSUtils.getVersion())) {
			header.addLine("##ngsutilsj__clearfilterVersion="+NGSUtils.getVersion());
		}
		
		VCFWriter writer = new VCFWriter(out, header);
		
		
		FileChannel channel = reader.getChannel();

		Iterator<VCFRecord> it = ProgressUtils.getIterator(reader.getFilename(), reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<VCFRecord>() {
            public String msg(VCFRecord current) {
                return current.getChrom()+":"+current.getPos();
            }}, new CloseableFinalizer<VCFRecord>());

		
		long count = 0;
		long filtered = 0;
		long clearedCount = 0;

		TallyValues<String> filterCounts = new TallyValues<String>();
		TallyValues<String> clearedCounts = new TallyValues<String>();
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
//			System.err.println(rec+" ;; " + !onlyOutputPass+" ;; "+!rec.isFiltered());

			boolean cleared = false;
			List<String> residualFilters = new ArrayList<String>();
			if (rec.isFiltered()) {
				if (clearOnly) {
					boolean only = true;
					for (String f: rec.getFilters()) {
						if (!clearFilters.contains(f)) {
							only = false;
						}
					}
					
					if (only) {
						String val = null;
						for (String f: rec.getFilters()) {
							cleared = true;
							clearedCounts.incr(f);
							if (val == null) {
								val = f;
 							} else {
 								val += "," + f;
 							}
						}
						if (val != null) {
							rec.getInfo().put("CG_CLEARED_FILTER", new VCFAttributeValue(val));
						}

					} else {
						residualFilters.addAll(rec.getFilters());
					}
					
				} else {
					for (String f: rec.getFilters()) {
						if (!clearFilters.contains(f)) {
							residualFilters.add(f);
						} else if (clearFilters.contains(f)) {
							cleared = true;
							clearedCounts.incr(f);
							if (rec.getInfo().contains("CG_CLEARED_FILTER")) {
								String existing = rec.getInfo().get("CG_CLEARED_FILTER").toString();
								existing += "," + f;
								rec.getInfo().put("CG_CLEARED_FILTER", new VCFAttributeValue(existing));
								
							} else {
								rec.getInfo().put("CG_CLEARED_FILTER", new VCFAttributeValue(f));
							}
						}
					}
				}
			}
			
			if (cleared) {
				clearedCount++;
			}
			
			rec.clearFilters();
			for (String f: residualFilters) {
				rec.addFilter(f);
			}
			
			
//			System.err.println(rec+" ;; " + !onlyOutputPass+" ;; "+!rec.isFiltered());
			if (!onlyOutputPass || !rec.isFiltered()) {
				count++;
				writer.write(rec);
			}
			if (rec.isFiltered()) {
				filtered++;
				for (String filter: rec.getFilters()) {
					filterCounts.incr(filter);
				}
			}
		}
		
		System.err.println(    "Wrote   : " + count + " variants");
		if (onlyOutputPass) {
			System.err.println("Filtered: " + filtered + " variants");
		}
		System.err.println("Cleared: " + clearedCount + " filters");
		System.err.println();
		for (String filter: clearedCounts.keySet()) {
			System.err.println(filter+": " +clearedCounts.getCount(filter));
		}
		
		reader.close();
		writer.close();
	}

}
