package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.vcf.VCFReader;


@Command(name="vcf-samples", desc="Output the sample names in a VCF file", category="vcf")
public class VCFSamples extends AbstractOutputCommand {
	private String filename = "-";

    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {
		
        if (filename == null) {
            throw new CommandArgumentException("You must specify a VCF file (or - for stdin)!");
        }
        
		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}

		for (String sample: reader.getHeader().getSamples()) {
			System.out.println(sample);
		}
		
		reader.close();
	}

}
