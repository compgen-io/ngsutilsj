package io.compgen.ngsutils.support.stats;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;

@Command(name="prop-test", desc="Yate's corrected ChiSquared (proportions) 2x2 table", category="stats", hidden=true)
public class YatesChiSqCli extends AbstractCommand {
	private int[] vals;

	@UnnamedArg(name="a b c d")
	public void setValues(String[] vals) throws CommandArgumentException {
		if (vals.length != 4) {
			throw new CommandArgumentException("You must specify exactly 4 values.");
		}
		
		this.vals = new int[] { Integer.parseInt(vals[0]), Integer.parseInt(vals[1]), Integer.parseInt(vals[2]), Integer.parseInt(vals[3]) }; 
	}
	
	@Exec
	public void exec() throws Exception {
	    System.out.println("    S\t| F");
		System.out.println("A | "+ vals[0]+"\t| "+vals[1]);
		System.out.println("B | "+ vals[2]+"\t| "+vals[3]);
		
		System.out.println("");
		System.out.println("Yate's corrected ChiSquared p-value:\t" + StatUtils.calcProportionalPvalue(vals[0], vals[1], vals[2], vals[3]));
	}
}
