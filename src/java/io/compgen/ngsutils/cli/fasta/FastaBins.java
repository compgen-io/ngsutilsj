package io.compgen.ngsutils.cli.fasta;

import java.io.File;
import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;

@Command(name="fasta-bins", desc="For an indexed FASTA file, calculate bins and write them to a BED file", doc="Bins are calculated using a given window size and step size. Bins are will include the ends of chromosomes and the final bin for a chromosome will be smaller than --window.", category="fasta")
public class FastaBins extends AbstractOutputCommand {
    private String filename = null;
    private int windowSize = -1;
    private int stepSize = -1;
    
    @Option(desc="Window size", name="window")
    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }    

    @Option(desc="Step size (default: window size)", name="step")
    public void setStepSize(int stepSize) {
        this.stepSize = stepSize;
    }    

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing FASTA filename!");
        }

        if (windowSize == -1) {
            throw new CommandArgumentException("--window is required!");
        }

        if (stepSize == -1) {
        	stepSize = windowSize;
        }

        
        if (filename.endsWith(".fai")) {
        	// no-op... 
        } else if (new File(filename+".fai").exists()) {
        	this.filename = this.filename + ".fai";
        } else {
            throw new CommandArgumentException("FASTA file must be indexed (missing: " + filename + ".fai)");
        }

        TabWriter tab = new TabWriter(out); // no header
        
        StringLineReader reader = new StringLineReader(filename);
        
        for (String line: IterUtils.wrap(reader.iterator())) {
        	String[] cols = StringUtils.strip(line).split("\t");
        	String ref = cols[0];
        	int length = Integer.parseInt(cols[1]); // should be smaller than 2 Gb (2^31)
        	
        	int pos = 0;
        	while (pos + windowSize < length) {
        		tab.write(ref);
        		tab.write(pos);
        		tab.write(pos + windowSize);
        		tab.eol();
        		pos += stepSize;
        	}
        	
    		tab.write(ref);
    		tab.write(pos);
    		tab.write(length);
    		tab.eol();
        }

        tab.close();
    }

}
