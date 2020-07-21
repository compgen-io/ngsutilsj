package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressStats;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-tocount", desc="Convert a VCF to a count file using the AD (or RO/AO) format field", category="vcf")
public class VCFToCount extends AbstractOutputCommand {
    private String vcfFilename=null;
    private String sampleName=null;
	
    private boolean onlyOutputPass = false;
    private boolean outputAF = false;
    private boolean outputTotal = false;
    private boolean useROAO = false;

    private boolean hetOnly = false;

    @Option(desc = "Only count heterozygous variants (requires format GT=0/1, only two values allowed)", name = "het")
    public void setHeterozygous(boolean hetOnly) {
        this.hetOnly = hetOnly;
    }

    @Option(desc="Use RO/AO format fields (default is to use AD)", name="use-ro-ao")
    public void setROAO(boolean val) {
        this.useROAO = val;
    }

    @Option(desc="Output total allele count", name="total")
    public void setOutputTotal(boolean val) {
        this.outputTotal = val;
    }

    @Option(desc="Output alternative allele frequency (from AD field)", name="af")
    public void setAF(boolean val) {
        this.outputAF = val;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
    }

    @Option(desc="Sample to use for vcf-counts (if VCF file has more than one sample)", name="sample")
    public void setSampleName(String sampleName) {
        this.sampleName = sampleName;
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
        vcfFilename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (vcfFilename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(vcfFilename);
		}

		if (!useROAO) {
			if (!reader.getHeader().getFormatIDs().contains("AD")) {
			    throw new CommandArgumentException("The VCF file must contain the \"AD\" format annotation to output allele frequencies from the VCF file.");
			}
		} else {
			if (!reader.getHeader().getFormatIDs().contains("RO")) {
			    throw new CommandArgumentException("The VCF file must contain the \"RO\" format annotation to output reference allele counts from the VCF file.");
			}
			if (!reader.getHeader().getFormatIDs().contains("AO")) {
			    throw new CommandArgumentException("The VCF file must contain the \"AO\" format annotation to output alternative allele counts from the VCF file.");
			}
		}

		int sampleIdx = 0;
        if (sampleName != null) {
            sampleIdx = reader.getHeader().getSamplePosByName(sampleName);
        }
		
		TabWriter writer = new TabWriter();
		
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## vcf-input: " + vcfFilename);

        writer.write("chrom");
        writer.write("pos");
        writer.write("ref");
        writer.write("alt");
        writer.write("ref_count");
        writer.write("alt_count");
        if (outputAF) {
            writer.write("alt_freq");
        }
        if (outputTotal) {
        	writer.write("total_count");
        }
        writer.eol();

        
        long total = 0;
        final long[] offset = new long[]{0,0}; // offset, curpos
        
        for (String chr: reader.getHeader().getContigNames()) {
            total += reader.getHeader().getContigLength(chr);
        }
        
        final VCFHeader header = reader.getHeader();
        final long totalF = total;
        
		for (VCFRecord record: IterUtils.wrap(ProgressUtils.getIterator(vcfFilename.equals("-") ? "variants <stdin>": vcfFilename, reader.iterator(), new ProgressStats(){

            @Override
            public long size() {
                return totalF;
            }

            @Override
            public long position() {
                return offset[0] + offset[1];
            }}, new ProgressMessage<VCFRecord>(){

                String curChrom = "";
                
                @Override
                public String msg(VCFRecord current) {
                    if (!current.getChrom().equals(curChrom)) {
                        if (!curChrom.equals("")) {
                            offset[0] += header.getContigLength(curChrom);
                        }
                        
                        curChrom = current.getChrom();
                    }

                    offset[1] = current.getPos();
                    
                    return current.getChrom()+":"+current.getPos();
                }}))) {
			if (onlyOutputPass && record.isFiltered()) {
				continue;
			}

			if (hetOnly) {
			    if (!record.getSampleAttributes().get(sampleIdx).contains("GT")) {
			        throw new CommandArgumentException("Missing GT field ("+record+")");
			    }

	            String val = record.getSampleAttributes().get(sampleIdx).get("GT").asString(null);
	            if (!val.equals("0/1") && !val.equals("0|1")) {
	                continue;
	            }
			}

			for (int i=0; i<record.getAlt().size(); i++) {
			    String alt = record.getAlt().get(i);
			    int refCount = -1;
			    int altCount = -1;
			    int totalCount = 0;
			    		
			    if (!useROAO) {
				    if (!record.getSampleAttributes().get(sampleIdx).contains("AD")) {
	                    throw new CommandArgumentException("Missing AD field ("+record+")");
				    }
				    String[] ad = record.getSampleAttributes().get(sampleIdx).get("AD").asString(null).split(",");
                    refCount = Integer.parseInt(ad[0]);
                    altCount = Integer.parseInt(ad[i+1]);
                    
                    totalCount = refCount;
                    for (int j=1; j<ad.length; j++) {
                    	totalCount += Integer.parseInt(ad[j]);
                    }
                     
			    } else {
				    if (!record.getSampleAttributes().get(sampleIdx).contains("RO")) {
	                    throw new CommandArgumentException("Missing RO field ("+record+")");
				    }
				    if (!record.getSampleAttributes().get(sampleIdx).contains("AO")) {
	                    throw new CommandArgumentException("Missing AO field ("+record+")");
				    }

				    String ro = record.getSampleAttributes().get(sampleIdx).get("RO").asString(null);
				    String[] ao = record.getSampleAttributes().get(sampleIdx).get("AO").asString(null).split(",");

                    refCount = Integer.parseInt(ro);
                    altCount = Integer.parseInt(ao[i]);

                    totalCount = refCount;
                    for (int j=0; j<ao.length; j++) {
                    	totalCount += Integer.parseInt(ao[j]);
                    }
                     

			    }

			    writer.write(record.getChrom());
                writer.write(record.getPos());
			    writer.write(record.getRef());
			    writer.write(alt);
                writer.write(refCount);
                writer.write(altCount);
                
                if (outputAF) {
                    writer.write(((double) altCount) / (altCount + refCount));
                }
                if (outputTotal) {
                    writer.write(totalCount);
                }
                writer.eol();
			}
		}


		reader.close();
		writer.close();
	}
    
}
