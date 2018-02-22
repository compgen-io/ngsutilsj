package io.compgen.ngsutils.tabix;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnknownArgs;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.tabix.BGZFile;


@Command(name="tabix", desc="Query a tabix file", category="help", hidden=true)
public class TabixQuery extends AbstractOutputCommand {
	private String filename = "-";
	private GenomeSpan span;
    
    @UnknownArgs()
    public void setTest(String val) throws CommandArgumentException {
        System.err.println("Arg? " + val);
    }


	@UnnamedArg(name = "filename region (note: must be Tabix indexed (TBI or CSI))", required=true)
    public void setFilename(String[] args) throws CommandArgumentException {
        if (args.length != 2) {
            throw new CommandArgumentException("Invalid argument!");
            
        }
        
    	this.filename = args[0];
    	this.span = GenomeSpan.parse(args[1]);
    	
    }

	@Exec
	public void exec() throws Exception {		
		BGZFile bgzf = new BGZFile(filename);

		String s = bgzf.queryWithin(span.ref, span.start, span.end);
		System.err.println(span.ref +":"+ span.start+","+ span.end);
		if (s != null) {
	        System.out.println(s);
		}
		bgzf.close();
}

}
