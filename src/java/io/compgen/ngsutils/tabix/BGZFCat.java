package io.compgen.ngsutils.tabix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

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
        if (args.length > 1) {
            outfile = args[1];
        } else {
            outfile = "-";
        }
    }

    @Exec
    public void exec() throws Exception {
        
        final RandomAccessFile raf = new RandomAccessFile(infile, "r");
        final BGZFile bgzf = new BGZFile(raf);
        final InputStream is = new BufferedInputStream(new BGZInputStream(bgzf));

        final OutputStream os;
        if (outfile.equals("-")) {
            os = System.out;
        } else{
            os = new BufferedOutputStream(new FileOutputStream(outfile));
        }
        
        int c = 0;
        while ((c = is.read()) != -1) {
            os.write(c);
        }
        
        is.close();
        os.close();
    }

}
