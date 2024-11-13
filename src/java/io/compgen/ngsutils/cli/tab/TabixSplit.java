package io.compgen.ngsutils.cli.tab;

import java.util.ArrayList;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.tabix.BGZWriter;
import io.compgen.ngsutils.tabix.TabixFile;

@Command(name = "tabix-split", desc = "Splits a tabix file by ref/chrom", category = "annotation")
public class TabixSplit extends AbstractOutputCommand {
    private String infile;
    private String templFilename = null;
    private boolean header = false;
    
    @Option(desc="Output file template ({} will be replaced by the ref/chrom name, default based on infile)", name="templ")
    public void setTemplateName(String templFilename) {
        this.templFilename = templFilename;
    }

    @Option(desc="Write the header to all files", name="header")
    public void setHeader(boolean val) {
        this.header = val;
    }
    
    @UnnamedArg(name = "infile", required = true)
    public void setFilename(String fname) throws CommandArgumentException {
        infile = fname;
    }

    @Exec
    public void exec() throws Exception {
    	if (templFilename == null) {
    		if (infile.endsWith(".gz")) {
    			templFilename = infile.substring(0, infile.length()-3) + ".{}.gz";
    		} else if (infile.endsWith(".bgz")) {
    			templFilename = infile.substring(0, infile.length()-4) + ".{}.bgz";
    		} else {
    			templFilename = infile + ".{}.gz";
    		}
    	}
    	
    	if (!templFilename.contains("{}")) {
    		throw new CommandArgumentException("Missing {} in template filename");
    	}

        TabixFile tabix  = new TabixFile(infile, verbose);
        String curSeq = null;
        
        BGZWriter bgz = null;
        
        List<String> headerLines = new ArrayList<String>();
        
        int lineno = 0;
        for (String line: IterUtils.wrap(tabix.lines())) {
        	if (lineno < tabix.getSkipLines()) {
        		if (header) {
        			headerLines.add(line);
        		}
            	lineno ++;
        		continue;
        	}
    		String[] vals = line.split("\\t");
    		String seq = vals[tabix.getColSeq()-1];
    		
    		if (curSeq == null || !curSeq.equals(seq)) {
    			if (bgz != null) {
    				bgz.close();
    			}
    			curSeq = seq;
    			bgz = new BGZWriter(templFilename.replaceAll("\\{\\}", curSeq));
    			if (header) {
    				for (String hl: headerLines) {
    					bgz.writeString(hl+"\n");
    				}
    			}
    			System.err.println("Writing: "+templFilename.replaceAll("\\{\\}", curSeq));
    		}

    		bgz.writeString(line+"\n");
    		
		}
		if (bgz != null) {
			bgz.close();
		}
    }
    
}
