package io.compgen.ngsutils.tabix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;

@Command(name = "bgzip-cat", desc = "Decompress a bgzip file", category = "help", hidden = true)
public class BGZFCat extends AbstractOutputCommand {
    private String infile;
    private String outfile;

    @UnnamedArg(name = "infile outfile", required = true)
    public void setFilename(String[] args) throws CommandArgumentException {
        infile = args[0];
        outfile = args[1];
    }

    @Exec
    public void exec() throws Exception {
        final InputStream is = new BufferedInputStream(new BGZInputStream(infile));
        final OutputStream os = new BufferedOutputStream(new FileOutputStream(outfile));
        
        int c = 0;
        while ((c = is.read()) != -1) {
            os.write(c);
        }
        
        is.close();
        os.close();
    }

}
