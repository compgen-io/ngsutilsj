package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.vcf.VCFAnnotationDef;
import io.compgen.ngsutils.vcf.VCFContigDef;
import io.compgen.ngsutils.vcf.VCFFilterDef;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;


@Command(name="vcf-header-info", desc="Extract annotation/named fields from a VCF file", category="vcf")

public class VCFHeaderInfo extends AbstractOutputCommand {
	private String filename = null;
	private boolean extractInfo = false;
	private boolean extractFormat = false;
	private boolean extractSamples = false;
	private boolean extractFilters = false;
	private boolean extractContigs = false;
    
    @UnnamedArg(name = "input1.vcf", required=true)
    public void setFilenames(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

    @Option(desc="Output INFO fields", name="info")
    public void setExtractInfo(boolean val) {
        this.extractInfo = val;
    }

    @Option(desc="Output FORMAT fields", name="format")
    public void setExtractFormat(boolean val) {
        this.extractFormat = val;
    }

    @Option(desc="Output SAMPLE fields", name="sample")
    public void setExtractSample(boolean val) {
        this.extractSamples = val;
    }

    @Option(desc="Output FILTERS fields", name="filters")
    public void setExtractFilters(boolean val) {
        this.extractFilters = val;
    }

    @Option(desc="Output CONTIG fields", name="contig")
    public void setExtractContigs(boolean val) {
        this.extractContigs = val;
    }

    
	@Exec
	public void exec() throws Exception {
		if (this.filename == null) {
    		throw new CommandArgumentException("You need to specify at least two input VCF files.");
		}
		
		int acc = 0;
		if (extractInfo) {
			acc++;
		}
		if (extractFormat) {
			acc++;
		}
		if (extractSamples) {
			acc++;
		}
		if (extractFilters) {
			acc++;
		}
		if (extractContigs) {
			acc++;
		}
		if (acc == 0 || acc > 1) {
    		throw new CommandArgumentException("You need to one (and only one) field type to output (--info, --format, --filter, --samples, --contigs)");
		}
		
		VCFReader reader = new VCFReader(filename);
		VCFHeader header = reader.getHeader();

		TabWriter tab = new TabWriter(out);
		
		if (extractInfo) {
			for (String id: header.getInfoIDs()) {
				VCFAnnotationDef info = header.getInfoDef(id);
				tab.write(info.id);
				tab.write(info.description);
				tab.eol();
			}
		}
		if (extractFormat) {
			for (String id: header.getFormatIDs()) {
				VCFAnnotationDef fmt = header.getFormatDef(id);
				tab.write(fmt.id);
				tab.write(fmt.description);
				tab.eol();
			}
		}
		if (extractSamples) {
			for (String sample: header.getSamples()) {
				tab.write_line(sample);
			}
		}
		if (extractFilters) {
			for (String id: header.getFilterIDs()) {
				VCFFilterDef filter = header.getFilterDef(id);
				tab.write(filter.id);
				tab.write(filter.description);
				tab.eol();
			}
		}
		if (extractContigs) {
			for (String id: header.getContigNames()) {
				VCFContigDef contig = header.getContigDef(id);
				tab.write(contig.id);
				tab.write(contig.length);
				tab.eol();
			}
		}

		tab.close();
		reader.close();
		
	}

}
