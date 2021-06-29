package io.compgen.ngsutils.cli.vcf;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.vcf.VCFReader;
import io.compgen.ngsutils.vcf.VCFRecord;


@Command(name="vcf-tstv", desc="Calculate a Ts/TV ratio for SNVs", category="vcf")
public class VCFTsTvRatio extends AbstractOutputCommand {
	private String filename = "-";
	private boolean onlyUsePasssing = false;

    @Option(desc="Only use passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyUsePasssing) {
        this.onlyUsePasssing = onlyUsePasssing;
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

		long tsCount = 0;
		long tvCount = 0;
		
		for (VCFRecord rec: IterUtils.wrap(reader.iterator())) {
            if (onlyUsePasssing && rec.isFiltered()) {
                continue;
            }
            
            int tstv = rec.calcTsTv();
            if (tstv == -1) {
            	tsCount++;
            } else if (tstv == 1) {
            	tvCount++;
            }
            
		}		
		reader.close();
        System.out.println("Transitions (Ts)\t"+tsCount);
        System.out.println("Transversions (Tv)\t"+tvCount);
        System.out.println("Ts/Tv ratio\t"+((double)tsCount)/tvCount);
	}


}