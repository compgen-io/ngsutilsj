package io.compgen.ngsutils.cli.fastq;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TallyCounts;
import io.compgen.common.TallyValues;
import io.compgen.common.io.PassthruInputStream;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Command(name = "fastq-stats", desc = "Statistics about a FASTQ file", category="fastq")
public class FastqStats extends AbstractOutputCommand {
    private String filename = null;
    private boolean pipe = false;
    private boolean intab = false;
    private boolean calcAdapter = false;

    private String adapterIllumina = "AGATCGGAAGAG";  // From Universal Adapters, usable on first and second reads
    
    
    public FastqStats() {
    }

    @UnnamedArg(name="FILE1")
    public void setFilenames(String filename) throws IOException {
        this.filename = filename;
    }

    @Option(desc="Pipe input file to stdout (for streaming)", name="pipe")
    public void setRedirectInput(boolean pipe) {
        this.pipe = pipe;
    }

    @Option(desc="Calculate adapter pos (Illumina adapters)", name="adapters")
    public void setCalcAdapter(boolean calcAdapter) {
        this.calcAdapter =calcAdapter;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must supply a FASTQ file.");
        }

        if (out == System.out && pipe) {
            throw new CommandArgumentException("You can't write the report and pipe input both to stdout!");
        }

        InputStream is = null;
        FileChannel channel = null;
        String name = null;
        
        if (filename.equals("-")) {
            is = System.in;
            name = "<stdin>";
        } else {
            File f = new File(filename);
            is = new FileInputStream(f);
            channel = ((FileInputStream)is).getChannel();
            name = f.getName();
        }

        if (pipe) {
            is = new PassthruInputStream(is, System.out);
        }

        
        boolean interleaved = false;

        String lastName = null;
        int readnum = 1;
        
        long fragmentCount = 0;
        int maxlen1 = 0;
        int maxlen2 = 0;
        
        TallyCounts readLength1 = new TallyCounts();
        TallyCounts readLength2 = new TallyCounts();
        
        TallyCounts readGC1 = new TallyCounts();
        TallyCounts readGC2 = new TallyCounts();
        
        List<TallyValues<Character>> baseFreq1 = new ArrayList<TallyValues<Character>>();;
        List<TallyValues<Character>> baseFreq2 = new ArrayList<TallyValues<Character>>();;
        
        List<TallyCounts> baseQual1 = new ArrayList<TallyCounts>();;
        List<TallyCounts> baseQual2 = new ArrayList<TallyCounts>();;
        
        TallyCounts adapterPos1 = new TallyCounts();
        TallyCounts adapterPos2 = new TallyCounts();
        
        TallyCounts medianQvals1 = new TallyCounts();
        TallyCounts medianQvals2 = new TallyCounts();
        
        FastqReader reader = Fastq.open(is, null, channel, name);

        for (FastqRead read: reader) {
            if (lastName == null || !lastName.equals(read.getName())) {
                fragmentCount ++;
                lastName = read.getName();
                readnum = 1;
            } else {
                if (read.getName().equals(lastName)) {
                    interleaved = true;
                    readnum = 2;
                }
            }
            
            TallyCounts readQvals = new TallyCounts();
            
            if (readnum == 1) {
                // read length
                readLength1.incr(read.getQual().length());
                if (read.getQual().length() > maxlen1) {
                    maxlen1 = read.getQual().length();
                }

                // GC % over the read
                int gc = 0;

                // base call frequency
                String upper = read.getSeq().toUpperCase();
                for (int i=0; i<upper.length(); i++) {
                    if (baseFreq1.size() <= i) {
                        baseFreq1.add(new TallyValues<Character>());
                    }

                    baseFreq1.get(i).incr(upper.charAt(i));
                    
                    if (upper.charAt(i) == 'G' || upper.charAt(i) == 'C') {
                        gc++;
                    }
                }
                readGC1.incr(100 * gc / upper.length());
                
                // base quality frequency
                for (int i=0; i<read.getQual().length(); i++) {
                    if (baseQual1.size() <= i) {
                        baseQual1.add(new TallyCounts());
                    }
                    int qual = read.getQual().charAt(i) - 33;
                    baseQual1.get(i).incr(qual);
                    readQvals.incr(qual);
                }
                    
                medianQvals1.incr((int)readQvals.getMedian());
                
                if (calcAdapter) {
                    for (int i=0; i<upper.length()-adapterIllumina.length(); i++ ) {
                        if (upper.substring(i, i+adapterIllumina.length()).equals(adapterIllumina)) {
                            adapterPos1.incr(i);
                            break;
                        }
                    }
                }
                
            } else {
                // read length
                readLength2.incr(read.getQual().length());
                if (read.getQual().length() > maxlen2) {
                    maxlen2 = read.getQual().length();
                }

                // GC % over the read
                int gc = 0;

                // base call frequency
                String upper = read.getSeq().toUpperCase();
                for (int i=0; i<upper.length(); i++) {
                    if (baseFreq2.size() <= i) {
                        baseFreq2.add(new TallyValues<Character>());
                    }
                    
                    baseFreq2.get(i).incr(upper.charAt(i));

                    if (upper.charAt(i) == 'G' || upper.charAt(i) == 'C') {
                        gc++;
                    }
                }
                readGC2.incr(100 * gc / upper.length());
                
                // base quality frequency
                for (int i=0; i<read.getQual().length(); i++) {
                    if (baseQual2.size() <= i) {
                        baseQual2.add(new TallyCounts());
                    }
                    int qual = read.getQual().charAt(i) - 33;
                    baseQual2.get(i).incr(qual);
                    readQvals.incr(qual);
                }

                medianQvals2.incr((int)readQvals.getMedian());

                if (calcAdapter) {
                    for (int i=0; i<upper.length()-adapterIllumina.length(); i++ ) {
                        if (upper.substring(i, i+adapterIllumina.length()).equals(adapterIllumina)) {
                            adapterPos2.incr(i);
                            break;
                        }
                    }
                }
            }
            
        }
        reader.close();

        println("Fragment-count:\t"+fragmentCount);
        println("Interleaved:\t"+(interleaved?"yes":"no"));
        
        println();
        println("read-length\tmin\t5%\t25%\t50%\t75%\t95%\tmax");
        printtab("read1");
        printtab(readLength1.getMin());
        printtab(readLength1.getQuantile(0.05));
        printtab(readLength1.getQuantile(0.25));
        printtab(readLength1.getQuantile(0.50));
        printtab(readLength1.getQuantile(0.75));
        printtab(readLength1.getQuantile(0.95));
        printtab(readLength1.getMax());
        println();

        if (interleaved) {
            printtab("read2");
            printtab(readLength2.getMin());
            printtab(readLength2.getQuantile(0.05));
            printtab(readLength2.getQuantile(0.25));
            printtab(readLength2.getQuantile(0.50));
            printtab(readLength2.getQuantile(0.75));
            printtab(readLength2.getQuantile(0.95));
            printtab(readLength2.getMax());
            println();
        }

        println();
        println("base-call-freq-read1\tA\tC\tG\tT");
        for (int i=0; i<baseFreq1.size(); i++) {
            double total = baseFreq1.get(i).getTotal();
            long A = baseFreq1.get(i).getCount('A');
            long C = baseFreq1.get(i).getCount('C');
            long G = baseFreq1.get(i).getCount('G');
            long T = baseFreq1.get(i).getCount('T');
            
            printtab(i+1);
            printtab(String.format("%.3f", A/total));
            printtab(String.format("%.3f", C/total));
            printtab(String.format("%.3f", G/total));
            printtab(String.format("%.3f", T/total));
            println();
        }
        if (interleaved) {
            println();
            println("base-call-freq-read2\tA\tC\tG\tT");
            for (int i=0; i<baseFreq2.size(); i++) {
                double total = baseFreq2.get(i).getTotal();
                long A = baseFreq2.get(i).getCount('A');
                long C = baseFreq2.get(i).getCount('C');
                long G = baseFreq2.get(i).getCount('G');
                long T = baseFreq2.get(i).getCount('T');
                
                printtab(i+1);
                printtab(String.format("%.3f", A/total));
                printtab(String.format("%.3f", C/total));
                printtab(String.format("%.3f", G/total));
                printtab(String.format("%.3f", T/total));
                println();
            }
        }
        
        println();
        println("base-qual-dist-read1\tmin\t5%\t25%\t50%\t75%\t95%\tmax");
        for (int i=0; i<baseQual1.size(); i++) {
            printtab(i+1);
            printtab(baseQual1.get(i).getMin());
            printtab(baseQual1.get(i).getQuantile(0.05));
            printtab(baseQual1.get(i).getQuantile(0.25));
            printtab(baseQual1.get(i).getQuantile(0.50));
            printtab(baseQual1.get(i).getQuantile(0.75));
            printtab(baseQual1.get(i).getQuantile(0.95));
            printtab(baseQual1.get(i).getMax());
            println();
        }
        if (interleaved) {
            println();
            println("base-qual-dist-read2\tmin\t5%\t25%\t50%\t75%\t95%\tmax");
            for (int i=0; i<baseQual2.size(); i++) {
                printtab(i+1);
                printtab(baseQual2.get(i).getMin());
                printtab(baseQual2.get(i).getQuantile(0.05));
                printtab(baseQual2.get(i).getQuantile(0.25));
                printtab(baseQual2.get(i).getQuantile(0.50));
                printtab(baseQual2.get(i).getQuantile(0.75));
                printtab(baseQual2.get(i).getQuantile(0.95));
                printtab(baseQual2.get(i).getMax());
                println();
            }
        }
        

        
        println();
        println("gc-pct-read1\tcount");
        for (int i=readGC1.getMin(); i<=readGC1.getMax(); i++) {
            printtab(i);
            printtab(readGC1.getCount(i));
            println();
        }
        if (interleaved) {
            println();
            println("gc-pct-read2\tcount");
            for (int i=readGC2.getMin(); i<=readGC2.getMax(); i++) {
                printtab(i);
                printtab(readGC2.getCount(i));
                println();
            }
        }
        
        println();
        println("median-read-qual-read1\tcount");
        for (int i=medianQvals1.getMin(); i<=medianQvals1.getMax(); i++) {
            printtab(i);
            printtab(medianQvals1.getCount(i));
            println();
        }
        if (interleaved) {
            println();
            println("median-read-qual-read2\tcount");
            for (int i=medianQvals2.getMin(); i<=medianQvals2.getMax(); i++) {
                printtab(i);
                printtab(medianQvals2.getCount(i));
                println();
            }
        }        

        if (calcAdapter) {
            println();
            println("adapter-counts-at-pos-read1\tcount");
            for (int i=0; i<=maxlen1; i++) {
                printtab(i+1);
                printtab(adapterPos1.getCount(i));
                println();
            }
            if (interleaved) {
                println();
                println("adapter-counts-at-pos-read2\tcount");
                for (int i=0; i<=maxlen2; i++) {
                    printtab(i+1);
                    printtab(adapterPos2.getCount(i));
                    println();
                }
            }
        }
    }
    

    
    private void println() throws IOException {
        println("");
    }
    
    private void printtab(int i) throws IOException {
        printtab(""+i);
    }
    
    private void printtab(long i) throws IOException {
        printtab(""+i);
    }
    
    private void printtab(String s) throws IOException {
        if (intab) {
            out.write(("\t"+s).getBytes());
        } else {
            out.write(s.getBytes());
            intab = true; 
        }
    }

    private void println(String s) throws IOException {
        out.write((s+"\n").getBytes());
        intab = false; 
    }

}

