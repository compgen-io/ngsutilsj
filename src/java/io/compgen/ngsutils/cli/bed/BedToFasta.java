package io.compgen.ngsutils.cli.bed;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.FastaRecord;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="bed-tofasta", desc="Extract FASTA sequences based on BED coordinates", category="bed")
public class BedToFasta extends AbstractOutputCommand {
    
    private String bedFilename = null;
    private String fastaFilename = null;
    
    private boolean ignoreStrand = false;
    private int wrap = -1;
    
    @Option(name="ns", desc="Ignore strand (always return \"+\" strand)")
    public void setIgnoreStrand(boolean ignore) {
        this.ignoreStrand = ignore;
    }
    
    @Option(name="wrap", desc="Wrap output sequence")
    public void setWrap(int wrap) {
        this.wrap = wrap;
    }
    
    @UnnamedArg(name = "bedFile fastaFile")
    public void setFilename(String[] filenames) throws CommandArgumentException {
        if (filenames.length != 2) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        bedFilename = filenames[0];
        fastaFilename = filenames[1];
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        FastaReader fasta = FastaReader.open(fastaFilename);
        
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(bedFilename))) {
            GenomeSpan coord = record.getCoord();
            String name;
            String seq = fasta.fetchSequence(coord.ref, coord.start, coord.end);
            if (ignoreStrand || coord.strand == Strand.PLUS || coord.strand == Strand.NONE) {
                name = record.getName() + "|" + coord.ref + ":" + coord.start + "-" + coord.end;
            } else {
                name = record.getName() + "|" + coord.ref + ":" + coord.end + "-" + coord.start;
                seq = SeqUtils.revcomp(seq);
            }
            
            new FastaRecord(name, seq).write(out, wrap);
        }
    }
}
