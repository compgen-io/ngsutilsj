package org.ngsutils.cli.splicing;

import java.io.IOException;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fasta.FastaReader;
import org.ngsutils.fasta.IndexedFastaFile;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj fasta-junctions")
@Command(name="fasta-junctions", desc="Extract sequences flanking a junction.", cat="fasta", doc="Junctions should be specified as ref:start-end, where start and end are the 0-based coordinates that mark the *intronic* parts of the junction. Junctions can also be semi-colon delimited to include more that one event per line.")
public class FastaJunctions extends AbstractOutputCommand {
    
    private String fastaName = null;
    private String juncName = null;
    private GenomeRegion junction = null;
    private int wrap = 60;
    private int size = 100;
    private boolean markJunction = false;
    private boolean markOverlap = false;
    
    @Unparsed(name = "FILE chrom:start-end")
    public void setArgs(List<String> args) {
        fastaName = args.get(0);
        if (args.size()>1) {
            junction = GenomeRegion.parse(args.get(1), true);
        }
    }

    @Option(description = "Junction list filename - col 1 junction, col 2 name (optional)", longName="file", defaultToNull=true)
    public void setJunctionFilename(String juncName) {
        this.juncName = juncName;
    }

    @Option(description = "Mark junction with [] for primer design", longName="mark")
    public void setMarkJunction(boolean mark) {
        this.markJunction = mark;
        if (markOverlap) {
            markOverlap = false;
        }
    }

    @Option(description = "Mark junction with - for primer overlap", longName="overlap")
    public void setMarkOverlap(boolean mark) {
        this.markOverlap = mark;
        if (markJunction) {
            markJunction = false;
        }
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
        
        FastaReader fasta = new IndexedFastaFile(fastaName);
        
        if (junction != null) {
            processRegion(fasta, junction);
        } else {
            for (final String line : new StringLineReader(juncName)) {
                String stripped = StringUtils.strip(line);
                if (verbose) {
                    System.err.println(stripped);
                }
                if (stripped.length() > 0) {
                    String[] cols = StringUtils.strip(line).split("\t");
                    String name = null;
                    if (cols.length > 1) {
                        name = cols[1];
                    }
                    
                    String[] junctions = cols[0].split(";");
                    for (int i=0; i< junctions.length; i++) {
                        GenomeRegion junc = GenomeRegion.parse(junctions[i], true);
                        processRegion(fasta, junc, (junctions.length > 1) ? name+"-"+(i+1) : name);
                    }
                }
            }
        }
        
        fasta.close();        
    }
    
    protected void processRegion(FastaReader fasta, GenomeRegion region) throws IOException {
        processRegion(fasta, region, null);
    }
    protected void processRegion(FastaReader fasta, GenomeRegion region, String name) throws IOException {
        int up_start = region.start - size;
        int down_end = region.end + size;

        String upseq = fasta.fetch(region.ref, up_start, region.start);
        String downseq = fasta.fetch(region.ref, region.end, down_end);
        String seq;
        
        if (markJunction) {
            seq = upseq.substring(0, upseq.length()-1);
            seq += "[" + upseq.charAt(upseq.length()-1);
            seq += downseq.charAt(0) + "]";
            seq += downseq.substring(1);
        } else if (markOverlap) {
            seq = upseq + "-" + downseq;
        } else {
            seq = upseq + downseq;
        }

        if (name == null) {
            writeSeq(region.ref+":"+up_start+"-"+region.start+","+region.end+"-"+down_end, seq);
        } else {
            writeSeq(name, seq, region.ref+":"+up_start+"-"+region.start+","+region.end+"-"+down_end);
        }
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
