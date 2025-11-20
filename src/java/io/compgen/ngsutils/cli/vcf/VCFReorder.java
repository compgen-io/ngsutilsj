package io.compgen.ngsutils.cli.vcf;

import java.util.ArrayList;
import java.util.HashMap;
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
import io.compgen.common.StringLineReader;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.ListUtils;
import io.compgen.ngsutils.vcf.VCFAttributes;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-reorder", desc="Reorders samples in a VCF file", category="vcf")
public class VCFReorder extends AbstractOutputCommand {
	private String filename = "-";
	private List<String> newSamples = null;
	private String sampleFile = null;

    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }
    @Option(desc="File containing the new order of samples", name="samples-file")
    public void setSampleFile(String val) throws CommandArgumentException {
    	sampleFile = val;
    }

    @Option(desc="Names of samples (comma delimited, or multiple)", name="sample", charName="s", allowMultiple=true)
    public void setSample(String val) throws CommandArgumentException {
    	if (newSamples == null) { 
    		newSamples = new ArrayList<String>();
    	}
    	for (String s: val.split(",")) {
    		newSamples.add(s.trim());
    	}
    }

	@Exec
	public void exec() throws Exception {
		
        if (sampleFile == null && newSamples == null) {
            throw new CommandArgumentException("You must specify either --sample or --samples-file!");
        }
        if (sampleFile != null && newSamples != null) {
            throw new CommandArgumentException("You must specify either --sample or --samples-file!");
        }
        if (filename == null) {
            throw new CommandArgumentException("You must specify a VCF file (or - for stdin)!");
        }
        
        if (sampleFile != null) {
        	newSamples = new ArrayList<String>();
        	for (String line: new StringLineReader(sampleFile)) {
        		newSamples.add(line);
        	}
        }
        
		VCFReader reader;
		
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}

		VCFHeader header = reader.getHeader().clone(true);
		header.addLine("##ngsutilsj_vcf_reorderCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_reorderVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_reorderVersion="+NGSUtils.getVersion());
		}

		List<String> origSampleOrder = reader.getHeader().getSamples();
		Map<String, Integer> origSampleIndex = new HashMap<String, Integer>();

		for (int idx=0; idx < origSampleOrder.size(); idx++) {
			origSampleIndex.put(origSampleOrder.get(idx), idx);
		}
		
		int[] newSampleOrigIndex = new int[newSamples.size()];
		for (Pair<Integer, String> pair: new ListUtils<String>(newSamples).enumerate()) {
			if (origSampleIndex.containsKey(pair.two)) {
				newSampleOrigIndex[pair.one] = origSampleIndex.get(pair.two);
				header.addSample(pair.two);
			} else {
				// maybe the value is the sample number? 1, 2, 3?
				try {
					int oldidx = Integer.parseInt(pair.two) - 1; // convert sample 1 to zero-based array index 0 
					if (oldidx < origSampleOrder.size()) {
						newSampleOrigIndex[pair.one] = oldidx;
						header.addSample(pair.two);
					} else {
						System.err.println("Sample number out of range. Got: "+pair.one+", Max: "+origSampleOrder.size());					
					}
				} catch (NumberFormatException e) {
					System.err.println("Missing sample: " + pair.two);					
				}
			}			
		}
		
		VCFWriter writer = new VCFWriter(out, header);
		
		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
			List<VCFAttributes> formatVals = rec.getSampleAttributes();			
			List<VCFAttributes> newFormatValues = new ArrayList<VCFAttributes>();
			
			for (int i=0; i<newSampleOrigIndex.length; i++) {
				newFormatValues.add(formatVals.get(newSampleOrigIndex[i]));
			}

			VCFRecord outrec = new VCFRecord(rec.getChrom(), rec.getPos(), rec.getDbSNPID(), 
					rec.getRef(), rec.getAlt(), rec.getQual(), rec.getFilters(), rec.getInfo(), 
					newFormatValues, rec.getAltOrig(), header);
			writer.write(outrec);
		}
		reader.close();
	}
}
