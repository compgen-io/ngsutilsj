package io.compgen.ngsutils.cli.tab;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.tabix.BGZFile;
import io.compgen.ngsutils.tabix.BGZFile.BGZBlock;
import io.compgen.ngsutils.tabix.TabixFile;

@Command(name = "tabix-cat", desc = "Decompress a Tabix file", category = "help", hidden = true)
public class TabixCat extends AbstractOutputCommand {
    private String infile;

    @UnnamedArg(name = "infile", required = true)
    public void setFilename(String fname) throws CommandArgumentException {
        infile = fname;
    }

    @Exec
    public void exec() throws Exception {
        if (verbose) {
            TabixFile file = new TabixFile(infile, verbose);
            file.dumpIndex();
          
	          for (String line: IterUtils.wrap(file.lines())) {
	              System.out.println(line);
	          }
	        file.close();
	        System.exit(0);
        }
        
        BGZFile bgz = new BGZFile(infile);
        BGZBlock block = bgz.readCurrentBlock();
        while (block != null) {
        	System.out.write(block.uBuf);
            block = bgz.readCurrentBlock();
        }
        System.out.flush();
    }
}
