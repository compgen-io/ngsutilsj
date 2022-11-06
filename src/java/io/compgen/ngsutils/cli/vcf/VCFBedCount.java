package io.compgen.ngsutils.cli.vcf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.math3.distribution.PoissonDistribution;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-bedcount", desc="For a given BED file, count the number of variants present in the BED region", doc="Note: this expects both the BED and VCF files to be sorted.", category="vcf")
public class VCFBedCount extends AbstractOutputCommand {
    private String vcfFilename=null;
    private String bedFilename=null;
    private String sampleName=null;

    private double tmb=-1;
    private boolean onlyPassing=false;
    private boolean altOnly=false;


    @Option(desc = "Given a genome-wide TMB value (variants per megabase), calculate a p-value for the given count (Poisson test, P(X >= count, lambda=tmb)); Note: If you use this, your bin sizes should be larger than 1Mb", name = "tmb")
    public void setTMB(double tmb) {
        this.tmb = tmb;
    }

    @Option(desc = "Only count alt variants (requires GT FORMAT field, exports all non GT:0/0)", name = "alt")
    public void setAltOnly(boolean altOnly) {
        this.altOnly = altOnly;
    }


    @Option(desc="Only count passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyPassing) {
        this.onlyPassing = onlyPassing;
    }

    @Option(desc="Sample to use for vcf-counts (if VCF file has more than one sample, default: first sample)", name="sample")
    public void setSampleName(String sampleName) {
        this.sampleName = sampleName;
    }
    
    
    @UnnamedArg(name = "input.bed input.vcf", required=true)
    public void setFilename(String[] filenames) throws CommandArgumentException {
    	if (filenames.length!=2) {
    	    throw new CommandArgumentException("You must include a BED file and a VCF file.");
    	}
        bedFilename = filenames[0];
        vcfFilename = filenames[1];
    }

	
    @Exec
	public void exec() throws Exception {		

		VCFReader reader;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}

		int sampleIdx = 0;
        if (sampleName != null) {
            sampleIdx = reader.getHeader().getSamplePosByName(sampleName);
        }

        Map<String, Map<Pair<Integer, Integer>, Integer>>  counts = new HashMap<String, Map<Pair<Integer, Integer>, Integer>>();
        
        Iterator<BedRecord> bedIt = BedReader.readFile(bedFilename);
        for (BedRecord bedRecord: IterUtils.wrap(bedIt)) {
        	if (!counts.containsKey(bedRecord.getCoord().ref)) {
        		counts.put(bedRecord.getCoord().ref, new HashMap<Pair<Integer, Integer>, Integer>());
        	}
        	counts.get(bedRecord.getCoord().ref).put(new Pair<Integer, Integer>(bedRecord.getCoord().start, bedRecord.getCoord().end), 0);
        }
        
        
		for (VCFRecord record: IterUtils.wrap(ProgressUtils.getIterator(vcfFilename.equals("-") ? "variants <stdin>": vcfFilename, reader.iterator(), null))) {
			if (onlyPassing && record.isFiltered()) {
				continue;
			}

			String ref = record.getChrom();
			int pos = record.getPos()-1; // zero-based
			

			if (altOnly) {
			    if (record.getSampleAttributes() == null || record.getSampleAttributes().get(sampleIdx) == null || !record.getSampleAttributes().get(sampleIdx).contains("GT")) {
			        throw new CommandArgumentException("Missing GT field");
			    }

	            String val = record.getSampleAttributes().get(sampleIdx).get("GT").asString(null);
			
	            // if 0/0 or 1/1, etc -- skip
                if (val.indexOf('/')>-1) {
                    String[] v2 = val.split("/");
                    if (v2.length == 2 && v2[0].equals("0") && v2[1].equals("0")) {
                        continue;
                    }
                } else if (val.indexOf('|')>-1) {
                    String[] v2 = val.split("\\|");
                    if (v2.length == 2 && v2[0].equals("0") && v2[1].equals("0")) {
                        continue;
                    }
                }
            }

			
			if (counts.containsKey(ref)) {
				for (Pair<Integer, Integer> coord: counts.get(ref).keySet()) {
					if (coord.one <= pos && coord.two > pos) {
						counts.get(ref).put(coord, counts.get(ref).get(coord)+1);
					}
				}
			}
		}

		reader.close();

		TabWriter writer = new TabWriter();
		
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## vcf-input: " + vcfFilename);
        writer.write_line("## bed-input: " + bedFilename);
        if (tmb > 0) {
            writer.write_line("## TMB: "+tmb);
        }


        writer.write("chrom");
        writer.write("start");
        writer.write("end");
        writer.write("varcount");
        writer.write("variants_per_mb");
        if (tmb > 0) {
            writer.write("poisson_pvalue");
        }
        writer.eol();

        Map<Integer, PoissonDistribution> ppois = new HashMap<Integer, PoissonDistribution>();
        
        Iterator<BedRecord> bedIt2 = BedReader.readFile(bedFilename);
        for (BedRecord bedRecord: IterUtils.wrap(bedIt2)) {
        	String ref = bedRecord.getCoord().ref;
        	int start = bedRecord.getCoord().start;
        	int end = bedRecord.getCoord().end;
        	int count = -1;
        	
        	for (Pair<Integer, Integer> pair: counts.get(ref).keySet()) {
        		if (pair.one == start && pair.two == end) {
        			count = counts.get(ref).get(pair);
        			break;
        		}
        	}
        	
        	writer.write(ref);
        	writer.write(start);
        	writer.write(end);
        	writer.write(count);
        	
        	int length = end - start;
        	writer.write(count / (length / 1000000.0));

        	if (tmb > 0) {
        		if (!ppois.containsKey(length)) {
        			double lambda = tmb * length / 1000000;
        			ppois.put(length, new PoissonDistribution(lambda));
        		}
        		
        		writer.write(1-ppois.get(length).cumulativeProbability(count-1));
        	}
        	
        	
        	writer.eol();
        	
        	
        	
        }
		
		writer.close();
	}

}
