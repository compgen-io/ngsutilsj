package io.compgen.ngsutils.cli.tab;

import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.tabix.TabixFile;

@Command(name = "tabix-split", desc = "Splits a tabix file by ref/chrom", category = "annotation")
public class TabixSplit extends AbstractOutputCommand {
    private String infile;
    private String templFilename;

    @Option(desc="Output file template ({} will be replaced by the ref/chrom name)", name="templ")
    public void setTemplateName(String templFilename) {
        this.templFilename = templFilename;
    }


    
    @UnnamedArg(name = "infile", required = true)
    public void setFilename(String fname) throws CommandArgumentException {
        infile = fname;
    }

    @Exec
    public void exec() throws Exception {
    	GZIPOutputStream gzout = new java.util.zip.GZIPOutputStream(out);
    	
    	//gzout.
    	
        TabixFile file = new TabixFile(infile, verbose);
        if (verbose) {
            file.dumpIndex();
        }
        for (String line: IterUtils.wrap(file.lines())) {
            System.out.println(line);
        }
    }
}
