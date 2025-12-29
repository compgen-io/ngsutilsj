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


@Command(name="vcf-concat", desc="Concatenate VCF files that have different variants but the same samples.", category="vcf", doc=""
		+ "This command can be used to re-combine VCF files that have been split into \n"
		+ "several smaller chunks. Crucially, this command will merge the files \n"
		+ "in a sorted manner the same time.\n"
		+ "\n"
		+ "This assumes the VCF file is coordinate sorted and the chromosomes are in a \n"
		+ "consistent order. If present in the header, chromosome order will be \n"
		+ "determined by the order of ##contig lines.\n"
		+ "\n"
		+ "If the files have different INFO, FORMAT, or FILTER elements, they will all \n"
		+ "be present in the output file.")

public class VCFConcat extends AbstractOutputCommand {
	private String[] filenames = null;
    
    @UnnamedArg(name = "input1.vcf...", required=true)
    public void setFilenames(String[] filenames) throws CommandArgumentException {
    	this.filenames = filenames;
    }

	@Exec
	public void exec() throws Exception {
		if (filenames == null || filenames.length < 1) {
    		throw new CommandArgumentException("You need to specify at least one input VCF file.");
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

		for (int i=1; i<filenames.length; i++) {
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
			// TODO: actually check the order
			for (String contig: r.getHeader().getContigNames()) {
				if (!header.getContigNames().contains(contig)) {
		    		throw new CommandArgumentException("Unknown contig: " + contig +"! All chromosomes must be listed as a contig and the order must be the same between input files.");
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
			if (verbose) {
				System.err.println(contig+" ["+contigOrder.size()+"]");
			}
			contigOrder.put(contig, contigOrder.size());
		}
		
		VCFWriter writer = new VCFWriter(out, header);
		
		int[] curChrom = new int[readers.size()];
		int[] curPos = new int[readers.size()];
		
		boolean first = true;
		
		while (true) {
			boolean hasRecords = false;
			for (int i=0; i<readers.size(); i++) {
				if (curRecords.get(i) == null) {
					if (iterators.get(i).hasNext()) {
						VCFRecord rec = iterators.get(i).next();
						curRecords.set(i,  rec);

						if (!contigOrder.containsKey(rec.getChrom())) {
				    		throw new CommandArgumentException("Unknown chromosome: " + rec.getChrom()+"!\nAll chromosomes must be listed as a contig and the order must be the same between input files.");
						}
						
						curChrom[i] = contigOrder.get(rec.getChrom());
						curPos[i] = rec.getPos();

						hasRecords = true;
					} else {
						if (curChrom[i] != -1 && verbose) {
							System.err.println("Exhausted VCF file: "+filenames[i]);
						}
						curChrom[i] = -1;
						curPos[i] = -1;
					}
				} else {
					hasRecords = true;
				}
			}
			if (!hasRecords) {
				break;
			}
			
			if (first && verbose) {
				for (int i=0; i<readers.size(); i++) {
					System.err.println("["+i+"] "+curRecords.get(i).getChrom()+":"+curRecords.get(i).getPos()+" => "+curChrom[i]+","+curPos[i]);
				}
			}
			
			int lowIdx = -1;
			int lowChrIdx = -1;
			int lowPos = -1;
			
			for (int i=0; i<readers.size(); i++) {
				if (curChrom[i] > -1) {
					if (lowIdx == -1) {
						// not set, take this one.
						lowIdx = i;
						lowChrIdx = curChrom[i];
						lowPos = curPos[i];
					} else if (curChrom[i] < lowChrIdx) {
						// lower chrom, so choose this one.
						lowIdx = i;
						lowChrIdx = curChrom[i];
						lowPos = curPos[i];
					} else if (curChrom[i] == lowChrIdx && curPos[i] < lowPos) {
						// lower pos, so take this one.
						lowIdx = i;
						lowChrIdx = curChrom[i];
						lowPos = curPos[i];
					} else if (curChrom[i] == lowChrIdx && curPos[i] == lowPos){
			    		throw new CommandArgumentException("Overlapping variant positions found: "+  curRecords.get(i).getChrom() +":"+curPos[i]);
					}				
				}
			}
			if (first && verbose) {
				System.err.println("Writing: "+curRecords.get(lowIdx).getChrom()+":"+curRecords.get(lowIdx).getPos());
				System.err.println("["+lowIdx+"] "+curRecords.get(lowIdx).getChrom()+":"+curRecords.get(lowIdx).getPos()+" => "+curChrom[lowIdx]+","+curPos[lowIdx]);
				first = false;
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
