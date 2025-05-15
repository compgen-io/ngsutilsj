package io.compgen.ngsutils.cli.vcf;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-merge", desc="Combine multiple VCF files that have different annotations, but the same variants.", category="vcf", doc=""
		+ "This command is useful for workflows where a base VCF file is annotated by \n"
		+ "different tools in parallel. After all of the tools have completed, you can \n"
		+ "use this command to merge all the annotations back together.\n"
		+ "\n"
		+ "In a map/reduce context, this is the reduce step.\n"
		+ "\n"
		+ "Annotations are merged in order. In the event of a conflict, the first file \n"
		+ "on the command-line wins. If a variant is missing from any of the VCF files, \n"
		+ "an error will be thrown.")

public class VCFMerge extends AbstractOutputCommand {
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

		for (int i=1; i< filenames.length; i++) {
			VCFReader r = new VCFReader(filenames[i]);
			readers.add(r);
			iterators.add(r.iterator());
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
		
		header.addLine("##ngsutilsj_vcf_mergeCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_mergeVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_mergeVersion="+NGSUtils.getVersion());
		}
	
		VCFWriter writer = new VCFWriter(out, header);

		for (VCFRecord rec: IterUtils.wrap(primary.iterator())) {
			List<VCFRecord> secondary = new ArrayList<VCFRecord>();
			for (Iterator<VCFRecord> it: iterators) {
				VCFRecord next = it.next();
				secondary.add(next);
				if (!rec.getChrom().equals(next.getChrom()) || rec.getPos() != next.getPos() || !rec.getRef().equals(next.getRef())) {
					throw new Exception("Variants out of order! Expected: "+ rec.getChrom() + ":" + rec.getPos() + ":" + rec.getRef() + ", got: " + next.getChrom() + ":" + next.getPos() + ":" + next.getRef());
				}

				if (rec.getDbSNPID() == null || rec.getDbSNPID().equals("") || rec.getDbSNPID().equals(VCFRecord.MISSING)) {
					if (next.getDbSNPID() != null && !next.getDbSNPID().equals("") && !rec.getDbSNPID().equals(VCFRecord.MISSING)) {
						rec.setDbSNPID(next.getDbSNPID());
					}
				}
				
				if (rec.getAlt().size() != next.getAlt().size()) {
					throw new Exception("Variants out of order! Expected: "+ rec.getChrom() + ":" + rec.getPos() + ":" + StringUtils.join(",", rec.getAlt()) + ", got: " + next.getChrom() + ":" + next.getPos() + ":" + StringUtils.join(",", next.getAlt()));
				}
				
				for (int i=0; i<rec.getAlt().size(); i++) {
					String a = rec.getAlt().get(i);
					String b = next.getAlt().get(i);
					if (!a.equals(b)) {
						throw new Exception("Variants out of order! Expected: "+ rec.getChrom() + ":" + rec.getPos() + ":" + StringUtils.join(",", rec.getAlt()) + ", got: " + next.getChrom() + ":" + next.getPos() + ":" + StringUtils.join(",", next.getAlt()));
					}
				}
				
				if (next.getFilters() != null) {
					for (String filter: next.getFilters()) {
						if (rec.getFilters() == null || rec.getFilters().contains(filter)) {
							rec.addFilter(filter);
						}
					}
				}
				
				for (String k: next.getInfo().getKeys()) {
					if (!rec.getInfo().contains(k)) {
						rec.getInfo().put(k, next.getInfo().get(k));
					}
				}
				
				if (rec.getSampleAttributes()!=null) {
					for (int i=0; i<rec.getSampleAttributes().size(); i++) {
						for (String k: next.getSampleAttributes().get(i).getKeys()) {
							if (!rec.getSampleAttributes().get(i).contains(k)) {
								rec.getSampleAttributes().get(i).put(k, next.getSampleAttributes().get(i).get(k));
							}
						}
					}
				}
			}
			
			writer.write(rec);
		}
		
		for (VCFReader r: readers) {
			r.close();
		}
		
		writer.close();
	}


}
