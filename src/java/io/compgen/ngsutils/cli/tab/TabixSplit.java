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
    private boolean byRef = false;
    private int linenum = -1;
    
    @Option(desc="Output file template ({} will be replaced by the ref/chrom name, default based on infile)", name="templ")
    public void setTemplateName(String templFilename) {
        this.templFilename = templFilename;
    }

    @Option(desc="Split file by ref/chrom", name="by-ref")
    public void setByRef(boolean byRef) {
        this.byRef = byRef;
    }
    
    @Option(desc="Split file by number of lines", name="lines")
    public void setLines(int linenum) {
        this.linenum = linenum;
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
    	
    	if (linenum > 0 && byRef) {
    		throw new CommandArgumentException("You can only split --by-ref or --lines, but not both at the same time.");
    	}

    	if (!templFilename.contains("{}")) {
    		throw new CommandArgumentException("Missing {} in template filename");
    	}

        TabixFile tabix  = new TabixFile(infile, verbose);
        String curSeq = null;
        
        BGZWriter bgz = null;
        
        List<String> headerLines = new ArrayList<String>();
        
        int fileno = 0;
        int curLineNum = 0;        
        int skipLineNum = 0;
        boolean inHeader = true;
        
        for (String line: IterUtils.wrap(tabix.lines())) {
        	if (skipLineNum < tabix.getSkipLines() || (inHeader && line.length()>0 && line.charAt(0) == tabix.getMeta())) {
        		if (header) {
        			headerLines.add(line);
        		}
            	skipLineNum ++;
        		continue;
        	}
        	inHeader = false;
        	
    		String[] vals = line.split("\\t");
    		String seq = vals[tabix.getColSeq()-1];

    		if (byRef) {
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
    		} else {
    			if (fileno == 0 || curLineNum > this.linenum) {
	    			if (bgz != null) {
	    				bgz.close();
	    			}
    				curLineNum=0;
    				fileno++;
	    			bgz = new BGZWriter(templFilename.replaceAll("\\{\\}", ""+fileno));
	    			if (header) {
	    				for (String hl: headerLines) {
	    					bgz.writeString(hl+"\n");
	    				}
	    			}
	    			System.err.println("Writing: "+templFilename.replaceAll("\\{\\}", ""+fileno));
    			}    				
    		}

    		bgz.writeString(line+"\n");
    		if (!byRef) {
    			curLineNum++;
    		}
		}
		if (bgz != null) {
			bgz.close();
		}
    }    
}
