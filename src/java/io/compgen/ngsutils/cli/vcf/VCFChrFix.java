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


@Command(name="vcf-chrfix", desc="Changes the reference (chrom) format (Ensembl/UCSC)", category="vcf")
public class VCFChrFix extends AbstractOutputCommand {
	private String filename = "-";
	private boolean ucsc = false;
	private boolean ensembl = false;

    @Option(desc="UCSC references (chr1, chr2, etc...)", name="ucsc")
    public void setUCSC(boolean val) {
    	this.ucsc = val;
    }
    
    @Option(desc="Ensembl references (1, 2, etc...)", name="ensembl")
    public void setEnsembl(boolean val) {
    	this.ensembl = val;
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {
		if (!ucsc && !ensembl) {
    		throw new CommandArgumentException("You must set either --ucsc or --ensembl");
		}

		if (ucsc && ensembl) {
    		throw new CommandArgumentException("You must set either --ucsc or --ensembl, not both.");
		}

		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
		VCFHeader header = reader.getHeader();
		header.addLine("##ngsutilsj_vcf_chrfixCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_chrfixVersion="+NGSUtils.getVersion())) {
		    header.addLine("##ngsutilsj_vcf_chrfixVersion="+NGSUtils.getVersion());
		}

	
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header);
		} else {
			writer = new VCFWriter(out, header);
		}

		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
			if (ucsc && !rec.getChrom().startsWith("chr")) {
				rec.setChrom("chr"+rec.getChrom());
			} else if (ensembl && rec.getChrom().startsWith("chr")) {
				rec.setChrom(rec.getChrom().substring(3));
			}
			writer.write(rec);
		}		
		reader.close();
		writer.close();
	}

}
