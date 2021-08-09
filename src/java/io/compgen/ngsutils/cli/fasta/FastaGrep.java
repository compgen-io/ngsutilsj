package io.compgen.ngsutils.cli.fasta;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-grep", desc="Find subsequences (exact match) in a FASTA file", category="fasta", doc="Note: This method performs a brute-force search. Do not use this for whole-genome alignment purposes.")
public class FastaGrep extends AbstractOutputCommand {
    
    private String filename = null;
    private String bait = null;
    private int allowedMismatches = 0;
    private boolean revcomp = false;
    
    
    @Option(desc="Number of allowed mismatches (indels not allowed)", name="mismatches", defaultValue="0", charName="m")
    public void setAllowedMismatches(int allowedMismatches) throws CommandArgumentException {
        this.allowedMismatches = allowedMismatches;
    }

    @Option(desc="Also search the reverse compliment", name="revcomp")
    public void setRevcomp(boolean revcomp) throws CommandArgumentException {
        this.revcomp = revcomp;
    }

    
    @UnnamedArg(name = "FILE seq")
    public void setArgs(String[] args) throws CommandArgumentException {
        if (args.length != 2) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        filename = args[0];
        bait = args[1].toUpperCase();
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        StringLineReader reader = new StringLineReader(filename);
        String ref = null;
        int curPos = 0;
        String buf = null;
        
        String baitRC = SeqUtils.revcomp(bait);
        
        for (String line: IterUtils.wrap(reader.iterator())) {
        	line = line.trim();
            if (line.charAt(0) == '>') {
                ref = line.substring(1).split("\\s",2)[0];
                curPos = 0;
                buf = "";
            } else {
                buf += line.toUpperCase();
                while (buf.length() > bait.length()) {
                	int mismatch = 0;
                	for (int i=0; i<bait.length() && mismatch <= allowedMismatches; i++) {
                		if (!SeqUtils.nucleotideMatch(buf.charAt(i), bait.charAt(i))) {
                			mismatch++;
                		}
                	}
                	if (mismatch <= allowedMismatches) {
                		if (revcomp) {
                			System.out.println(ref + "\t"+ (curPos+1)+"\t+");
                		} else {
                			System.out.println(ref + "\t"+ (curPos+1));
                		}
                	}

                	if (revcomp) {
		            	mismatch = 0;
		            	for (int i=0; i<bait.length() && mismatch <= allowedMismatches; i++) {
	                		if (!SeqUtils.nucleotideMatch(buf.charAt(i), baitRC.charAt(i))) {
		            			mismatch++;
		            		}
		            	}
		            	if (mismatch <= allowedMismatches) {
		            		System.out.println(ref + "\t"+ (curPos+1) + "\t-");
		            	}
                	}
                	
                	buf = buf.substring(1);
                	curPos++;
                }
            }
        }
        
        reader.close();
    }
}
