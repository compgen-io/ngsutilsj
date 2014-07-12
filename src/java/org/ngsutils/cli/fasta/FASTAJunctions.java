package org.ngsutils.cli.fasta;

import java.io.IOException;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fasta.IndexedFASTAFile;
import org.ngsutils.fasta.FASTAReader;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj fasta-junctions")
@Command(name="fasta-junctions", desc="Extract sequences flanking a junction.", cat="fasta", doc="Junctions should be specified as ref:start-end, where start and end are the 0-based coordinates that mark the *intronic* parts of the junction.")
public class FASTAJunctions extends AbstractOutputCommand {
    
    private String fastaName = null;
    private String juncName = null;
    private GenomeRegion junction = null;
    private int wrap = 60;
    private int size = 100;
    
    @Unparsed(name = "FILE chrom:start-end")
    public void setArgs(List<String> args) {
        fastaName = args.get(0);
        if (args.size()>1) {
            junction = GenomeRegion.parse(args.get(1));
        }
    }

    @Option(description = "Junction list filename", longName="file", defaultToNull=true)
    public void setJunctionFilename(String juncName) {
        this.juncName = juncName;
    }

    @Option(description = "Length of flanking sequence (bp) (default: 100)", longName="size", defaultToNull=true)
    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (fastaName == null) {
            throw new ArgumentValidationException("Missing/Invalid arguments!");
        }
        
        FASTAReader fasta = new IndexedFASTAFile(fastaName);
        
        if (junction != null) {
            processRegion(fasta, junction);
        } else {
            for (final String line : new StringLineReader(juncName)) {
                String stripped = StringUtils.strip(line);
                if (verbose) {
                    System.err.println(stripped);
                }
                if (stripped.length() > 0) {
                    GenomeRegion junc = GenomeRegion.parse(StringUtils.strip(line));
                    processRegion(fasta, junc);
                }
            }
        }
        
        fasta.close();        
    }
    
    protected void processRegion(FASTAReader fasta, GenomeRegion region) throws IOException {
        int up_start = region.start - size;
        int down_end = region.end + size;

        String seq = fasta.fetch(region.ref, up_start, region.start);
        seq += fasta.fetch(region.ref, region.end, down_end);
        
        String name = region.ref+":"+up_start+"-"+region.start+","+region.end+"-"+down_end;
        writeSeq(name, seq, "("+region+")");
    }
    
    protected void writeSeq(String name, String seq) {
        writeSeq(name, seq, null);
    }
    
    protected void writeSeq(String name, String seq, String comment) {
        System.out.print(">"+name);
        if (comment != null) {
            System.out.print(" " + comment);
        }
        System.out.println();
        while (seq.length() > wrap) {
            System.out.println(seq.substring(0, wrap));
            seq = seq.substring(wrap);
        }
        System.out.println(seq);        
    }
}
