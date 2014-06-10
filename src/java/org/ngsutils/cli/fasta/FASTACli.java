package org.ngsutils.cli.fasta;

import java.io.IOException;
import java.util.List;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.fasta.IndexedFASTAFile;
import org.ngsutils.fasta.FASTAReader;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj faidx")
@Command(name="faidx", desc="Extract subsequences from an indexed FASTA file", cat="fasta")
public class FASTACli extends AbstractOutputCommand {
    
    private String filename = null;
    private GenomeRegion region = null;
    private int wrap = 60;
    
    @Unparsed(name = "FILE chrom:start-end")
    public void setArgs(List<String> args) {
        if (args.size() != 2) {
            throw new RuntimeException("Missing/Invalid arguments!");
        }
        filename = args.get(0);
        region = GenomeRegion.parse(args.get(1));
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        FASTAReader fasta = new IndexedFASTAFile(filename);
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
