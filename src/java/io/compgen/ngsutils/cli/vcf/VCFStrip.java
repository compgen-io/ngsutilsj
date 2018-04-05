package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.VCFWriter;


@Command(name="vcf-strip", desc="Remove all annotation and sample information (FILTER,INFO,FORMAT,dbSNP) but keep output in VCF format", category="vcf")
public class VCFStrip extends AbstractOutputCommand {
	private String filename = "-";

    private boolean onlyOutputPass = false;
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
        this.onlyOutputPass = onlyOutputPass;
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
		header.addLine("##ngsutilsj_vcf_stripCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_stripVersion="+NGSUtils.getVersion());
		}
	
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header, true);
		} else {
			writer = new VCFWriter(out, header, true);
		}

		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
            if (onlyOutputPass && rec.isFiltered()) {
                continue;
            }
	        writer.write(rec);
		}		
		reader.close();
		writer.close();
	}

}
