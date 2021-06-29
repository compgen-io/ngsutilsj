package io.compgen.ngsutils.cli.vcf;

import java.util.Iterator;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-check", desc="Validate a VCF file", category="vcf")
public class VCFCheck extends AbstractOutputCommand {
	private String filename = "-";
	private static boolean quiet = false;
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }
    @Option(desc="Quiet output (no progress bar)", name="quiet", charName="q")
    public void setQuiet(boolean quiet) {
    	VCFCheck.quiet = quiet;
    }

	@Exec
	public void exec() throws Exception {

		VCFReader reader = new VCFReader(filename);

		Iterator<VCFRecord> it = null;
		if (VCFCheck.isQuiet()) {
			it = reader.iterator();
		} else {
	        it  = ProgressUtils.getIterator(reader.getFilename(), 
	        		reader.iterator(), 
	        		(reader.getChannel() == null)? null : new FileChannelStats(reader.getChannel()), 
					new ProgressMessage<VCFRecord>() {
			            public String msg(VCFRecord current) {
			                return current.getChrom()+":"+current.getPos();
			            }}, 
					new CloseableFinalizer<VCFRecord>());
		}
		// We just iterate through the file. Errors will be caught in parsing.
		while (it.hasNext()) {
			it.next();
		}

		reader.close();
	}
	public static boolean isQuiet() {
		return quiet;
	}
}