package io.compgen.ngsutils.cli.fasta;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;

@Command(name="fasta-split", desc="Split a FASTA file into a new file for each sequence present", category="fasta")
public class FastaSplit extends AbstractCommand {
    
    private String filename = null;
    private String template = "";

    @Option(desc="Output template (new files will be named: template${name}.fa)", name="template", defaultValue="")
    public void setTemplate(String template) {
        this.template = template;
    }    


    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        
        StringLineReader reader = new StringLineReader(filename);
        BufferedOutputStream bos = null;
        
        for (String line: IterUtils.wrap(reader.iterator())) {
            if (line.charAt(0) == '>') {
                String name = line.substring(1).split("\\s",2)[0];
                
                if (bos != null) {
                    bos.close();
                }
                
                bos = new BufferedOutputStream(new FileOutputStream(template+name+".fa"));
                System.err.println(name);
            }
            bos.write((line+"\n").getBytes());
        }
        reader.close();
        if (bos != null) {
            bos.close();
        }
    }
}
