package io.compgen.ngsutils.cli.fasta;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringLineReader;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.fasta.FastaReader;

import java.io.IOException;

@Command(name="fasta-gc", desc="Determine the GC% for a given region or bins", category="fasta")
public class FastaGC extends AbstractOutputCommand {
    private String filename = null;
    private String bedFilename = null;
    private int binSize = -1;
    
    @Option(desc="Bin size", name="bins")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }    

    @Option(desc="BED file containing regions to count", name="bed")
    public void setBEDFile(String bedFilename) {
        this.bedFilename = bedFilename;
    }    
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }
        if (binSize == -1 && bedFilename == null) {
            throw new CommandArgumentException("Missing/invalid arguments! You must specify either --bed or --bins!");
        }

        FastaReader fasta = FastaReader.open(filename);
        TabWriter tab = new TabWriter(out);
        tab.write("chrom");
        tab.write("start");
        tab.write("end");
        tab.write("gc_fraction");
        tab.eol();

        if (binSize == -1) {
            StringLineReader bedReader = new StringLineReader(bedFilename);
            for (String line: IterUtils.wrap(bedReader.iterator())) {
                if (line.trim().length()>0){
                    String[] spl = line.split("\t");
                    String ref = spl[0];
                    int start = Integer.parseInt(spl[1]);
                    int end = Integer.parseInt(spl[2]);
                    
                    String seq = fasta.fetchSequence(ref, start, end);
                    tab.write(ref);
                    tab.write(start);
                    tab.write(end);
                    tab.write(calcGC(seq));
                    tab.eol();
                }
            }
            
            bedReader.close();

        } else {
            
            StringLineReader reader = new StringLineReader(filename);
            String ref = null;
            int currentBinStart = 0;
            String buf = null;
            
            for (String line: IterUtils.wrap(reader.iterator())) {
                if (line.charAt(0) == '>') {
                    if (buf != null) {
                        tabWrite(tab, ref, currentBinStart, buf);
                    }
                    
                    ref = line.substring(1).split("\\W",2)[0];
                    System.err.println(">"+ref);
                    currentBinStart = 0;
                    buf = "";

                } else {
                    buf += line.trim();

                    while (buf.length() > binSize) {    
                        tabWrite(tab, ref, currentBinStart, buf.substring(0, binSize));
                        currentBinStart = currentBinStart + binSize;
                        buf = buf.substring(binSize);
                    }
                }
            }

            while (buf != null && buf.length() > binSize) {
                tabWrite(tab, ref, currentBinStart, buf.substring(0, binSize));
                currentBinStart = currentBinStart + binSize;
                buf = buf.substring(binSize);
            }
            if (buf != null && buf.length() > 0) {
                tabWrite(tab, ref, currentBinStart, buf);
            }
            reader.close();
            tab.close();
        }
    }

    private void tabWrite(TabWriter tab, String ref, int start, String buf) throws IOException {
        tab.write(ref);
        tab.write(start);
        tab.write(start + buf.length());
        tab.write(calcGC(buf));
//        tab.write(buf);
        tab.eol();
    }
    
    private double calcGC(String seq) {
        int total = 0;
        int gc = 0;
        for (int i=0; i<seq.length(); i++) {
            switch(seq.charAt(i)) {
            case 'C':
            case 'c':
            case 'G':
            case 'g':
                gc++;
            case 'A':
            case 'a':
            case 'T':
            case 't':
                total++;
                break;
            }
        }
        
        if (total == 0) {
            return -1;
        }
        return ((double)gc) / total;
    }
}
