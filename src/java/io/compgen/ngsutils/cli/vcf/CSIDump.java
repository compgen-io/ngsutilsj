package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.vcf.support.BGZFile;


@Command(name="csi-dump", desc="Dump data from a CSI file", category="vcf", hidden=true)
public class CSIDump extends AbstractOutputCommand {
	private String filename = "-";
       
    @UnnamedArg(name = "input.csi", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		BGZFile reader = new BGZFile(filename);
//		reader.dumpIndex();
		
		String s = reader.query("chr17", 7_000_000, 8_000_000);
		System.out.println("chr17:7_000_000-8_000_000");
		System.out.println(s);
		
		s = reader.query("chr17", 7361258);
		System.out.println("");
		System.out.println("chr17:7361258 (zero)");
		System.out.println(s);
		reader.close();
}

}
