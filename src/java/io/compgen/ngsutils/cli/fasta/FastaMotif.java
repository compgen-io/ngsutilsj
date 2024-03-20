package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.fasta.FastaChunkRecord;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.pwm.AbstractMotifFinder;
import io.compgen.ngsutils.pwm.JasparPWM;
import io.compgen.ngsutils.pwm.SeqMotif;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-motif", desc="Scan a FASTA file for matches to a motif (DNA only)", category="fasta", doc=""
		+ "Given a motif or frequency matrix, this command will scan a FASTA file looking for \n"
		+ "regions that match the motif. The frequency table is converted to a position \n"
		+ "weight matrix and for each n-mer in the FASTA file, a score calculated. Scores \n"
		+ "range from -1 to 1")
public class FastaMotif extends AbstractOutputCommand {
    private String filename = null;
    private String motif = null;
    private String jasparFilename = null;
    private int pseudocount = -1;
    private int mismatches = 0;
    private boolean showAll = false;
    private boolean showPvalue = false;
    private double pvalueThres = 1.0;
    
    @Option(desc="Add this pseudocount to the frequency counts (PWM only)", name="pseudo", defaultValue="1")
    public void setPseudocount(int pseudocount) throws CommandArgumentException {
    	if (pseudocount < 0) {
    		throw new CommandArgumentException("Pseudocount must be greater than 0");
    	}
        this.pseudocount = pseudocount;
    }    
    
//    @Option(desc="Add this fraction to the frequency (motif only, 0-1.0)", name="pseudo-motif", defaultValue="0.01")
//    public void setPseudocount(double pseudofrac) throws CommandArgumentException {
//    	if (pseudofrac < 0.0 || pseudofrac > 1.0) {
//    		throw new CommandArgumentException("Pseudo fraction must be  between 0-1.0");
//    	}
//        this.pseudofrac = pseudofrac;
//    }    
    
    @Option(desc="Calculate p-value (permutation based on human DNA frequency)", name="pvalue")
    public void setShowPvalue(boolean val) {
        this.showPvalue = val;
    }    

    @Option(desc="P-value threshold (assumes --pvalue)", name="max-pvalue")
    public void setPvalueThres(double pvalueThres) {
    	showPvalue = true;
        this.pvalueThres = pvalueThres;
    }    

    @Option(desc="Show all scores, not just positive matches", name="all", hide=true)
    public void setShowAll(boolean val) {
        this.showAll = val;
    }    

    @Option(desc="Motif in IUPAC format", name="motif")
    public void setMotif(String motif) {
        this.motif = motif;
    }    
    

    @Option(desc="Allowed mismatches (motif only)", name="mismatches")
    public void setMismatches(int mismatches) {
        this.mismatches = mismatches;
    }    
    

    @Option(desc="Motif file containing the counts for each position in the matrix (JASPAR format))", name="pwm")
    public void setJasparFile(String jasparFilename) {
        this.jasparFilename = jasparFilename;
    }    
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws Exception {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        AbstractMotifFinder motifFinder;
        
        if (jasparFilename != null) {
        	motifFinder = new JasparPWM(jasparFilename, pseudocount);
        } else if (motif != null) {
        	motifFinder = new SeqMotif(motif, mismatches);
        } else {
            throw new CommandArgumentException("Missing motif (--motif or --pwm)!");
        }
        
                
        TabWriter writer = new TabWriter(out);
        
        writer.write("seq");
        writer.write("start");
        writer.write("end");
        writer.write("strand");
        writer.write("match");
        writer.write("score");
        if (showPvalue) {
        	writer.write("pvalue");
        }
        writer.eol();
        
        FastaReader reader = FastaReader.open(filename);
        String curRef = null;
        String buffer = "";
        int pos = -1;
        for (FastaChunkRecord rec: IterUtils.wrap(reader.iteratorChunk(motifFinder.getLength()))) {
        	
        	if (curRef == null || !curRef.equals(rec.name)) {
        		if (verbose) {
        			System.err.println(">"+rec.name);
        			System.err.flush();
        		}
        		curRef = rec.name;
        		buffer = rec.seq;
        		pos = 0;
        		continue;
        	}
        	
        	buffer += rec.seq.toUpperCase();
        	
        	while (buffer.length() >= motifFinder.getLength()) {
        		String sub = buffer.substring(0,motifFinder.getLength());

        		boolean good = true;
        		for (int i=0; good && i<sub.length(); i++) {
        			switch (sub.charAt(i)) {
        			case 'A':
        			case 'C':
        			case 'G':
        			case 'T':
        				break;
        			default:
        				good = false;
        				break;
        			}
        		}
        		
        		if (good) {        		        			
	        		double score = motifFinder.calcScore(sub);
	        		double pval = 0.0;
	        		if (showAll || score > 0) {
	        			if (showPvalue) {
	        				pval = motifFinder.calcPvalue(score);
	        			}
	
	        			if (pval <= pvalueThres) { 
	        				if (showAll || motif == null || score >= (motifFinder.getLength() - this.mismatches)) {
			        	        writer.write(rec.name);
			        	        writer.write(pos);
			        	        writer.write(pos+motifFinder.getLength());
			        	        writer.write("+");
			        	        writer.write(sub);
			        	        writer.write(score);
			        	        if (showPvalue) {
			        	        	writer.write(pval);
			        	        }
			        	        writer.eol();
	        				}
	        			}
	        		}
	
	        		String revcomp = SeqUtils.revcomp(sub);
	        		double score2 = motifFinder.calcScore(revcomp);
	        		double pval2 = 0.0;
	        		if (showAll || score2 > 0) {
	        			if (showPvalue) {
	        				pval2 = motifFinder.calcPvalue(score2);
	        			}
	
	        			if (pval2 <= pvalueThres) {
	        				if (showAll || motif == null || score2 >= (motifFinder.getLength() - this.mismatches)) {
			        	        writer.write(rec.name);
			        	        writer.write(pos);
			        	        writer.write(pos+motifFinder.getLength());
			        	        writer.write("-");
			        	        writer.write(revcomp);
			        	        writer.write(score2);
			        	        if (showPvalue) {
			        	        	writer.write(pval2);
			        	        }
			        	        writer.eol();
	        				}
	        			}
	        		}
        		}
        		
        		buffer = buffer.substring(1);
        		pos++;        		
        	}
        }
        reader.close();
        writer.close();
    }

}
