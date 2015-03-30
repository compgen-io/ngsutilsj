package io.compgen.ngsutils.cli.splicing;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.IndexedFastaFile;

import java.io.IOException;
import java.util.List;

@Command(name="junction-flank", desc="Extract sequences flanking a junction.", category="splicing", doc="Junctions should be specified as ref:start-end, where start and end are the 0-based coordinates that mark the *intronic* parts of the junction. Junctions can also be semi-colon delimited to include more that one event per line.")
public class FastaJunctions extends AbstractOutputCommand {
    
    private String fastaName = null;
    private String juncName = null;
    private GenomeSpan junction = null;
    private int wrap = 60;
    private int size = 100;
    private boolean markJunction = false;
    private boolean markOverlap = false;
    
    @UnnamedArg(name = "FILE chrom:start-end")
    public void setArgs(List<String> args) {
        fastaName = args.get(0);
        if (args.size()>1) {
            junction = GenomeSpan.parse(args.get(1), true);
        }
    }

    @Option(desc="Junction list filename - col 1 junction, col 2 name (optional)", name="file")
    public void setJunctionFilename(String juncName) {
        this.juncName = juncName;
    }

    @Option(desc="Mark junction with [] for primer design", name="mark")
    public void setMarkJunction(boolean mark) {
        this.markJunction = mark;
        if (markOverlap) {
            markOverlap = false;
        }
    }

    @Option(desc="Mark junction with - for primer overlap", name="overlap")
    public void setMarkOverlap(boolean mark) {
        this.markOverlap = mark;
        if (markJunction) {
            markJunction = false;
        }
    }

    @Option(desc="Length of flanking sequence (bp) (default: 100)", name="size")
    public void setSize(int size) {
        this.size = size;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException {
        if (fastaName == null) {
            throw new CommandArgumentException("Missing/Invalid arguments!");
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
                        GenomeSpan junc = GenomeSpan.parse(junctions[i], true);
                        processRegion(fasta, junc, (junctions.length > 1) ? name+"-"+(i+1) : name);
                    }
                }
            }
        }
        
        fasta.close();        
    }
    
    protected void processRegion(FastaReader fasta, GenomeSpan region) throws IOException {
        processRegion(fasta, region, null);
    }
    protected void processRegion(FastaReader fasta, GenomeSpan region, String name) throws IOException {
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
