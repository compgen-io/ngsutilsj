package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.FastaRecord;
import io.compgen.ngsutils.pwm.JasparPWM;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-pwm", desc="Scan a FASTA file for matches to a motif (DNA only)", category="fasta", doc=""
		+ "Given a motif frequency matrix, this command will scan a FASTA file looking for \n"
		+ "regions that match the motif. The frequency table is converted to a position \n"
		+ "weight matrix and for each n-mer in the FASTA file, a score calculated. Scores \n"
		+ "range from -1 to 1")
public class FastaPWM extends AbstractOutputCommand {
    private String filename = null;
    private String jasparFilename = null;
    private int pseudocount = -1;
    private boolean showAll = false;
    private boolean showPvalue = false;
    private double pvalueThres = 1.0;
    
    @Option(desc="Add this pseudocount to the frequency counts", name="pseudo", defaultValue="1")
    public void setPseudocount(int pseudocount) throws CommandArgumentException {
    	if (pseudocount < 0) {
    		throw new CommandArgumentException("Pseudocount must be greater than 0");
    	}
        this.pseudocount = pseudocount;
    }    
    
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

    @Option(desc="Motif file containing the counts for each position in the matrix (JASPAR format))", name="motif")
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

        if (jasparFilename == null) {
            throw new CommandArgumentException("Missing motif file!");
        }
        
        JasparPWM pwm = new JasparPWM(jasparFilename, pseudocount);
        
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
        for (FastaRecord rec: IterUtils.wrap(reader.iterator())) {
        	for (int i=0; i<rec.seq.length()-pwm.getLength(); i++) {
        		String sub = rec.seq.substring(i,i+pwm.getLength());
        		
        		double score = pwm.calcScore(sub);
        		double pval = 0.0;
        		if (showAll || score > 0) {
        			if (showPvalue) {
        				pval = pwm.calcPvalue(score);
        			}

        			if (pval <= pvalueThres) {
	        	        writer.write(rec.name);
	        	        writer.write(i);
	        	        writer.write(i+pwm.getLength());
	        	        writer.write("+");
	        	        writer.write(sub);
	        	        writer.write(score);
	        	        if (showPvalue) {
	        	        	writer.write(pval);
	        	        }
	        	        writer.eol();
        			}
        		}

        		String revcomp = SeqUtils.revcomp(sub);
        		double score2 = pwm.calcScore(revcomp);
        		double pval2 = 0.0;
        		if (showAll || score2 > 0) {
        			if (showPvalue) {
        				pval2 = pwm.calcPvalue(score2);
        			}

        			if (pval2 <= pvalueThres) {
	        	        writer.write(rec.name);
	        	        writer.write(i);
	        	        writer.write(i+pwm.getLength());
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
        reader.close();
        writer.close();
    }

}
