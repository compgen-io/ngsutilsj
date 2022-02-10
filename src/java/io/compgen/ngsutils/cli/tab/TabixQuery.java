package io.compgen.ngsutils.cli.tab;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.tabix.TabixFile;

@Command(name = "tabix", desc = "Query a tabix file", category = "annotation", hidden = false)
public class TabixQuery extends AbstractOutputCommand {
    private String filename = "-";
    private GenomeSpan span;
    private String[] alleles = null;
    private int altCol = -1;
    private String bedFilename = null;

    @Option(name = "bed", desc = "Extract regions from this BED file")
    public void setBED(String bedFilename) throws CommandArgumentException {
        this.bedFilename = bedFilename;
    }

    @Option(name = "altcol", defaultValue = "-1", desc = "Alternate allele column (for matching a specific allele)")
    public void setAltCol(int altCol) throws CommandArgumentException {
        this.altCol = altCol - 1;
    }

    @UnnamedArg(name = "filename region {allele1,allele2} (note: must be Tabix indexed (TBI or CSI))", required = true)
    public void setFilename(String[] args) throws CommandArgumentException {
        if (args.length < 1) {
            throw new CommandArgumentException("Invalid argument!");
        }

        filename = args[0];
        
        if (args.length >1) {
	        span = GenomeSpan.parse(args[1]);
	
	        if (args.length > 2) {
	            alleles = args[2].split(",");
	        }
        }

    }

    @Exec
    public void exec() throws Exception {

    	if (span == null && bedFilename == null) {
            throw new CommandArgumentException( "You must include either a region of interest or specify --bed");
    		
    	}
    	
        if (altCol == -1 && alleles !=null ) {
            throw new CommandArgumentException(
                    "You must set --altcol to query by specific alleles!");
        }

        final TabixFile tabix = new TabixFile(filename);
        

        if (span != null) {
	        for (String line: IterUtils.wrap(tabix.query(span.ref, span.start, span.end))) {
	            if (line != null) {
	                if (alleles != null) {
	                    for (final String alt : alleles) {
	                        final String[] spl = line.split("\t");
	                        if (alt.equals(spl[altCol])) {
	                            System.out.println(line);
	                        }
	                    }
	                } else {
	                    System.out.println(line);
	                }
	            }
	        }
        } else if (bedFilename != null) {
        	for (BedRecord bed: IterUtils.wrap(BedReader.readFile(bedFilename))) {
    	        for (String line: IterUtils.wrap(tabix.query(bed.getCoord()))) {
    	            if (line != null) {
    	                if (alleles != null) {
    	                    for (final String alt : alleles) {
    	                        final String[] spl = line.split("\t");
    	                        if (alt.equals(spl[altCol])) {
    	                            System.out.println(line);
    	                        }
    	                    }
    	                } else {
    	                    System.out.println(line);
    	                }
    	            }
    	        }
        	}
        }
        
        tabix.close();
    }

}
