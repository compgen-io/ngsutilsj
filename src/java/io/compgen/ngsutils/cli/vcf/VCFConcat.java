package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-concat", desc="Concatenate VCF files that have the same annotations, but different variants.", category="vcf", doc=""
		+ "This command can be used to re-combine VCF files that have been split into \n"
		+ "several smaller chunks. Crucially, this command will merge the files \n"
		+ "in a sorted manner the same time.\n"
		+ "\n"
		+ "This assumes the VCF file is coordinate sorted and the chromosomes are in a"
		+ "consistent order. If present in the header, chromosome order will be \n"
		+ "determined by the order of ##contig lines.\n")

public class VCFConcat extends AbstractOutputCommand {
	private String[] filenames = null;
    
    @UnnamedArg(name = "input1.vcf...", required=true)
    public void setFilenames(String[] filenames) throws CommandArgumentException {
    	this.filenames = filenames;
    }

	@Exec
	public void exec() throws Exception {
		if (filenames == null || filenames.length < 2) {
    		throw new CommandArgumentException("You need to specify at least two input VCF files.");
		}

		for (String fname: filenames) {
			if (!new File(fname).exists()) {
	    		throw new CommandArgumentException("Missing file: "+ fname);
			}
		}

		VCFReader primary = new VCFReader(filenames[0]);
		VCFHeader header = primary.getHeader();

		List<VCFReader> readers = new ArrayList<VCFReader>();
		List<Iterator<VCFRecord>> iterators = new ArrayList<Iterator<VCFRecord>>();
		List<VCFRecord> curRecords = new ArrayList<VCFRecord>();

		readers.add(primary);
		iterators.add(primary.iterator());
		curRecords.add(null);

		for (int i=1; i< filenames.length; i++) {
			VCFReader r = new VCFReader(filenames[i]);
			readers.add(r);
			iterators.add(r.iterator());
			curRecords.add(null);
			for (String sample: r.getHeader().getSamples()) {
				if (!header.getSamples().contains(sample)) {
		    		throw new CommandArgumentException("File contains an extra sample? "+ sample);
				}
			}

			for (String filter: r.getHeader().getFilterIDs()) {
				if (!header.getFilterIDs().contains(filter)) {
					header.addFilter(r.getHeader().getFilterDef(filter));
				}
			}

			for (String info: r.getHeader().getInfoIDs()) {
				if (!header.getInfoIDs().contains(info)) {
					header.addInfo(r.getHeader().getInfoDef(info));
				}
			}

			for (String format: r.getHeader().getFormatIDs()) {
				if (!header.getFormatIDs().contains(format)) {
					header.addFormat(r.getHeader().getFormatDef(format));
				}
			}
			for (String contig: r.getHeader().getContigNames()) {
				if (!header.getContigNames().contains(contig)) {
					header.addContig(r.getHeader().getContigDef(contig));
				}
			}
			for (String alt: r.getHeader().getAlts()) {
				if (!header.getAlts().contains(alt)) {
					header.addAlt(r.getHeader().getAltDef(alt));
				}
			}
			for (String line: r.getHeader().getLines()) {
				if (!header.contains(line)) {
					header.addLine(line);
				}
			}
		}
		
		header.addLine("##ngsutilsj_vcf_concatCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_concatVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_concatVersion="+NGSUtils.getVersion());
		}

		Map<String, Integer> contigOrder = new HashMap<String, Integer>();
		for (String contig: header.getContigNames()) {
			contigOrder.put(contig, contigOrder.size());
		}
		
		VCFWriter writer = new VCFWriter(out, header);

		for (int i=0; i<readers.size(); i++) {
			if (curRecords.get(i) == null && iterators.get(i).hasNext()) {
				curRecords.set(i,  iterators.get(i).next());
			}
			
			int lowIdx = -1;
			int lowChrIdx = -1;
			int lowPos = -1;
			
			for (int j=0; j< curRecords.size(); j++) {
				int chrIdx = contigOrder.get(curRecords.get(i).getChrom());
				int pos = curRecords.get(i).getPos();

				if (lowIdx == -1) {
					lowIdx = j;
					lowChrIdx = chrIdx;
					lowPos = pos;
				} else if (chrIdx < lowChrIdx) {
					lowIdx = j;
					lowChrIdx = chrIdx;
					lowPos = pos;
				} else if (pos < lowPos) {
					lowIdx = j;
					lowChrIdx = chrIdx;
					lowPos = pos;
				} else {
		    		throw new CommandArgumentException("Overlapping variant positions found: "+  curRecords.get(i).getChrom() +":"+pos);
				}				
			}
			
			writer.write(curRecords.get(lowIdx));
			curRecords.set(lowIdx, null);
			
		}
		
		
		for (VCFReader r: readers) {
			r.close();
		}
		
		writer.close();
	}


}
