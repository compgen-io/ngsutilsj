package io.compgen.ngsutils.cli.tab;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.tabix.TabixFile;

@Command(name = "tabix", desc = "Query a tabix file", category = "help", hidden = false)
public class TabixQuery extends AbstractOutputCommand {
    private String filename = "-";
    private GenomeSpan span;
    private String[] alleles = null;
    private int altCol = -1;

    @Option(name = "altcol", defaultValue = "-1", desc = "Alternate allele column (for matching a specific allele)")
    public void setAltCol(int altCol) throws CommandArgumentException {
        this.altCol = altCol - 1;
    }

    @UnnamedArg(name = "filename region {allele1,allele2} (note: must be Tabix indexed (TBI or CSI))", required = true)
    public void setFilename(String[] args) throws CommandArgumentException {
        if (args.length < 2) {
            throw new CommandArgumentException("Invalid argument!");

        }

        filename = args[0];
        span = GenomeSpan.parse(args[1]);

        if (args.length > 2) {
            alleles = args[2].split(",");
        }

    }

    @Exec
    public void exec() throws Exception {

        if (altCol == -1 && alleles !=null ) {
            throw new CommandArgumentException(
                    "You must set --altcol to query by specific alleles!");
        }

        final TabixFile tabix = new TabixFile(filename);
        
//        if (verbose) {
//            tabix.dumpIndex();
//        }

        for (String line: IterUtils.wrap(tabix.query(span.ref, span.start, span.end))) {
        // System.err.println(span.ref +":"+ span.start+","+ span.end);
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
        tabix.close();
    }

}
