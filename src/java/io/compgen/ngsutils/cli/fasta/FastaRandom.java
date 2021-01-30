package io.compgen.ngsutils.cli.fasta;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.ngsutils.support.SeqUtils;

@Command(name="fasta-random", desc="Generate random DNA sequences", category="fasta")
public class FastaRandom extends AbstractOutputCommand {
    private int length = -1;
    private int count = -1;
    
    @Option(desc="Length", name="len", required=true)
    public void setLength(int length) {
        this.length = length;
    }    

    @Option(desc="Number of sequences to generate", name="count", charName="c", required=true)
    public void setCount(int count) {
        this.count = count;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (count <0 || length <0) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        
        for (int i=0; i< count; i++) {
        	System.out.println(">seq"+(i+1));
        	System.out.println(SeqUtils.generateRandomSeq(length));
        }
    }
}
