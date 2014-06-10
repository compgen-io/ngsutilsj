package org.ngsutils.cli.gtf;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.Annotator;
import org.ngsutils.annotation.GTFAnnotator;
import org.ngsutils.annotation.GTFAnnotator.GTFGene;
import org.ngsutils.annotation.GenomeAnnotation;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj gtf-export")
@Command(name="gtf-tobed", desc="Export gene annotations from a GTF file as BED regions", cat="gtf")
public class GTFExport extends AbstractOutputCommand {
    private String filename=null;
    private String whitelist = null;
    
    private boolean exportGene = false;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Whitelist", longName="whitelist")
    public void setWhitelist(String whitelist) {
        this.whitelist = whitelist;
    }

    @Option(description = "Export whole gene region", longName="gene")
    public void getGene(boolean val) {
        exportGene = true;
    }


    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (filename == null) {
            throw new NGSUtilsException("You must specify a GTF file! (- for stdin)");
        }
        
        TabWriter writer = new TabWriter();

        Set<String> whitelistSet = null;
        if (whitelist!=null) {
            if (verbose) {
                System.err.print("Reading whitelist: "+whitelist);
            }
            
            whitelistSet = new HashSet<String>();
            for (final String line : new StringLineReader(whitelist)) {
                whitelistSet.add(StringUtils.strip(line));
            }
            
            if (verbose) {
                System.err.println(" [done]");
            }
        }
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+filename);
        }

        Annotator<GTFGene> ann = new GTFAnnotator(filename);
        
        if (verbose) {
            System.err.println(" [done]");
        }

        for (GenomeAnnotation<GTFGene> ga:ann.allAnnotations()) {
            GTFGene gene = ga.getValue();
            if (whitelistSet != null) {
                if (!whitelistSet.contains(gene.getGeneName())) {
                    continue;
                }
            }
            if (exportGene) {
                writer.write(gene.getRef());
                writer.write(gene.getStart());
                writer.write(gene.getEnd());
                writer.write(gene.getGeneName());
                writer.write(0);
                writer.write(gene.getStrand().toString());
                writer.eol();
            }
        }
        
        writer.close();
    }
}
