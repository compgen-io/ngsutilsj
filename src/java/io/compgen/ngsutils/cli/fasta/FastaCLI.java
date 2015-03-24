package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.fasta.FastaReader;
import io.compgen.ngsutils.fasta.IndexedFastaFile;

import java.io.IOException;
import java.util.List;

@Command(name="faidx", desc="Extract subsequences from an indexed FASTA file", category="fasta")
public class FastaCLI extends AbstractOutputCommand {
    
    private String filename = null;
    private GenomeSpan region = null;
    private int wrap = 60;
    
    @UnnamedArg(name = "FILE chrom:start-end")
    public void setArgs(List<String> args) throws CommandArgumentException {
        if (args.size() != 2) {
            throw new CommandArgumentException("Missing/Invalid arguments!");
        }
        filename = args.get(0);
        region = GenomeSpan.parse(args.get(1));
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/Invalid arguments!");
        }
        FastaReader fasta = new IndexedFastaFile(filename);
        System.out.println(">"+region);
        String seq = fasta.fetch(region.ref, region.start-1, region.end);
        while (seq.length() > wrap) {
            System.out.println(seq.substring(0, wrap));
            seq = seq.substring(wrap);
        }
        System.out.println(seq);
        fasta.close();        
    }
}
