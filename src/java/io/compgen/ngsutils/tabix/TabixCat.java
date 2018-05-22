package io.compgen.ngsutils.tabix;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;

@Command(name = "tab-cat", desc = "Decompress a Tabix file", category = "help", hidden = true)
public class TabixCat extends AbstractOutputCommand {
    private String infile;

    @UnnamedArg(name = "infile", required = true)
    public void setFilename(String fname) throws CommandArgumentException {
        infile = fname;
    }

    @Exec
    public void exec() throws Exception {
        TabixFile file = new TabixFile(infile);
        for (String line: IterUtils.wrap(file.lines())) {
            System.out.println(line);
        }
    }
}
