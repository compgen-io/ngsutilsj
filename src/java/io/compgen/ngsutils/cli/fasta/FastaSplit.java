package io.compgen.ngsutils.cli.fasta;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
    private Set<String> valid = null;

    @Option(desc="Output template (new files will be named: template${name}.fa)", name="template", defaultValue="")
    public void setTemplate(String template) {
        this.template = template;
    }    


    @UnnamedArg(name = "FILE [seq1 seq2...]")
    public void setFilename(String[] filename) throws CommandArgumentException {
        this.filename = filename[0];
        if (filename.length > 1) {
        	valid = new HashSet<String>();
        	for (int i=1; i<filename.length; i++) {
        		valid.add(filename[i]);
        	}
        }
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
                
                if (valid == null || valid.contains(name)) {
	                bos = new BufferedOutputStream(new FileOutputStream(template+name+".fa"));
	                System.err.println(name);
                } else {
                	bos = null;
	                System.err.println(name + " (skip)");
                }
            }
            if (bos != null) {
            	bos.write((line+"\n").getBytes());
            }
        }
        reader.close();
        if (bos != null) {
            bos.close();
        }
    }
}
