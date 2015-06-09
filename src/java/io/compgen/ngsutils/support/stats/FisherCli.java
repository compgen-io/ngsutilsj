package io.compgen.ngsutils.support.stats;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;

@Command(name="fisher-test", desc="Calculate Fisher test 2x2 table", category="stats", hidden=true)
public class FisherCli extends AbstractCommand {
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
		System.out.println("A\t"+ vals[0]);
		System.out.println("B\t"+ vals[1]);
		System.out.println("C\t"+ vals[2]);
		System.out.println("D\t"+ vals[3]);

		System.out.println("");
		System.out.println("Fisher-exact exact p-value:\t" + new FisherExact().calcPvalue(vals));
		System.out.println("Fisher-exact left p-value:\t" + new FisherExact().calcLeftTailedPvalue(vals));
		System.out.println("Fisher-exact right p-value:\t" + new FisherExact().calcRightTailedPvalue(vals));
		System.out.println("Fisher-exact two-tailed p-value:\t" + new FisherExact().calcTwoTailedPvalue(vals));
	}
}
