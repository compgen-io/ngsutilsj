package io.compgen.ngsutils.cli.tab;

import java.io.IOException;
import java.util.Arrays;

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
    private String[] columns = null;


    @Option(name = "bed", desc = "Extract regions from this BED file")
    public void setBED(String bedFilename) throws CommandArgumentException {
        this.bedFilename = bedFilename;
    }

    @Option(name = "col", desc = "Extract a specific column", allowMultiple=true)
    public void setColumn(String colname) throws CommandArgumentException {
    	if (columns == null) {
    		columns = new String[] { colname };
    	} else {
    		columns = Arrays.copyOf(columns, columns.length + 1);
    		columns[columns.length-1] = colname;
    	}
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
        if (verbose) {
        	tabix.dumpIndex();
        }

        if (columns != null) {
        	if (tabix.getColBegin() == tabix.getColEnd()) {
        		System.out.print("seq\tpos");
        	} else {
        		System.out.print("seq\tstart\tend");
        	}
        	
        	for (String col:columns) {
        		System.out.print("\t" + col);
        	}
        	System.out.println("");
        }
        

        if (span != null) {
	        for (String line: IterUtils.wrap(tabix.query(span.ref, span.start, span.end))) {
	            if (line != null) {
	                if (alleles != null) {
	                    for (final String alt : alleles) {
	                        final String[] spl = line.split("\t");
	                        if (alt.equals(spl[altCol])) {
	                        	writeValue(tabix, line);
	                        }
	                    }
	                } else {
	                	writeValue(tabix, line);
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
    	                        	writeValue(tabix, line);
    	                        }
    	                    }
    	                } else {
    	                	writeValue(tabix, line);
    	                }
    	            }
    	        }
        	}
        }
        
        tabix.close();
    }

    private void writeValue(TabixFile tabix, String line) throws IOException {
    	if (columns == null) {
    		System.out.println(line);
    	} else {
    		String[] vals = line.split("\\t");
        	if (tabix.getColBegin() == tabix.getColEnd()) {
        		System.out.print(vals[tabix.getColSeq()-1]);
        		System.out.print("\t");
        		System.out.print(vals[tabix.getColBegin()-1]);
        	} else {
        		System.out.print(vals[tabix.getColSeq()-1]);
        		System.out.print("\t");
        		System.out.print(vals[tabix.getColBegin()-1]);
        		System.out.print("\t");
        		System.out.print(vals[tabix.getColEnd()-1]);
        	}
        	for (String col:columns) {
        		System.out.print("\t" + vals[tabix.findColumnByName(col)]);
        	}
    		System.out.println();
    	}
    }
    
}
