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
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

@Command(name = "tabix-concat", desc = "Re-combine split tabix files (natural sorting by filename)", category = "annotation")
public class TabixConcat extends AbstractOutputCommand {
    private String commentChar = "#";
    private int skipLines = 0;
    private int skipLines1 = 0;
    
    private List<String> infiles = new ArrayList<String>();
    
    @Option(desc="Comment/meta character (removed for files 2..N)", name="comment", defaultValue="#")
    public void setComment(String comment) {
    	if (comment != null && !comment.equals("")) {
    		this.commentChar = ""+comment.charAt(0);
    	}
    }

    @Option(desc="Skip (uncommented) lines (for files 2..N, to remove any header)", name="skipN")
    public void setSkip(int skipLines) {
    	this.skipLines = skipLines;
    }

    @Option(desc="Skip (uncommented) lines (for file 1)", name="skip1")
    public void setSkip1(int skipLines1) {
    	this.skipLines1 = skipLines1;
    }

    
    @UnnamedArg(name = "infiles...", required = true)
    public void setFilename(String[] fnames) throws CommandArgumentException {
    	for (String fname: fnames) {
    		infiles.add(fname);
    	}
    }

    @Exec
    public void exec() throws Exception {
    	if (infiles.size() == 0) {
    		throw new CommandArgumentException("You must specify at least one input file.");
    	}

    	List<String> sortedInfiles = StringUtils.naturalSort(infiles);

    	for (int i=0; i<sortedInfiles.size(); i++) {
    		System.err.println(sortedInfiles.get(i));    		
    		
    		int toSkip = skipLines; 
    		if (i == 0) {
    			toSkip = skipLines1;
    		}
    		
    		StringLineReader reader = new StringLineReader(sortedInfiles.get(i));
    		for (String line: IterUtils.wrap(reader.iterator())) {
    			if (i > 0 && line.startsWith(commentChar)) {
    				continue;
    			}
    			if (toSkip > 0) {
    				toSkip--;
    				continue;
    			}
    			out.write((line+"\n").getBytes());
    		}
    		reader.close();
    	}
    }    
}
