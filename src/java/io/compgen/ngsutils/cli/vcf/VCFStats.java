package io.compgen.ngsutils.cli.vcf;

import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.TallyValues;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-stats", desc="Summary statistics about a VCF file", category="vcf")
public class VCFStats extends AbstractOutputCommand {
	private String filename = "-";
	
    private boolean onlyPassing = false;
    private boolean showFullFilters = false;

    @Option(desc="Show full filter combinations", name="filter-combo")
    public void setShowFullFilters(boolean showFullFilters) {
        this.showFullFilters = showFullFilters;
    }
    
    @Option(desc="Only process passing variants", name="passing")
    public void setOnlyPassing(boolean onlyPassing) {
        this.onlyPassing = onlyPassing;
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
		
		FileChannel channel = reader.getChannel();

		Iterator<VCFRecord> it = ProgressUtils.getIterator(reader.getFilename(), reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<VCFRecord>() {
            public String msg(VCFRecord current) {
                return current.getChrom()+":"+current.getPos();
            }}, new CloseableFinalizer<VCFRecord>());

		
		long count = 0;
		long filtered = 0;
		long tsCount = 0;
		long tvCount = 0;

		TallyValues<String> filterCounts = new TallyValues<String>();
		TallyValues<String> fullFilterCounts = new TallyValues<String>();
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
			count++;

			if (rec.isFiltered()) {
				filtered++;
				for (String filter: rec.getFilters()) {
					filterCounts.incr(filter);
				}
				if (showFullFilters) {
					List<String> filters = new ArrayList<String>();
					filters.addAll(rec.getFilters());
					Collections.sort(filters);
					String key = StringUtils.join(",", filters);
					fullFilterCounts.incr(key);
				}
				if (onlyPassing) {
					continue;
				}
			}
			
            int tstv = rec.calcTsTv();

            if (tstv == -1) {
            	tsCount++;
            } else if (tstv == 1) {
            	tvCount++;
            }

			
		}
		
		System.out.println("Total variants:\t" + count);
		System.out.println("Filtered variants:\t" + filtered);
		System.out.println("Passing variants:\t" + (count - filtered));
		System.out.println();
		System.out.println("Transitions:\t" + tsCount);
		System.out.println("Transversions:\t" + tvCount);
		System.out.println("Ts/Tv ratio:\t" + ((double) tsCount / tvCount));
		System.out.println();
		System.out.println("[Filters]");
		for (String filter: filterCounts.keySet()) {
			System.out.println(filter+": " +filterCounts.getCount(filter));
		}
		
		if (showFullFilters) {
			System.out.println();
			System.out.println("[Filter combinations]");
			for (String filter: fullFilterCounts.keySet()) {
				System.out.println(filter+": " +fullFilterCounts.getCount(filter));
			}
		}

		reader.close();
	}

}
