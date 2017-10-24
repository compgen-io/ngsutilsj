package io.compgen.ngsutils.cli.fastq;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.TallyValues;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.ngsutils.fastq.filter.FilteringException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Command(name = "fastq-barcodesummary", desc = "Given Illumina 1.8+ naming, summarize the lane/barcodes included", category="fastq")
public class FastqBarcodeSummary extends AbstractCommand {
    private String filename = null;
    private boolean writeSplitConfig = false;
    private double minFraction = 0.0;

    public FastqBarcodeSummary() {
    }

    
    @Option(desc = "Write a config file for fastq-batchsplit", name="conf")
    public void setWriteSplitConfig(boolean writeSplitConfig) {
        this.writeSplitConfig = writeSplitConfig;
    }
    
    @Option(desc = "Only write lane/barcodes with at least this fraction (0.0-1.0)", name="frac", defaultValue="0.0")
    public void setMinFraction(double minFraction) {
        this.minFraction = minFraction;
    }
    
    @UnnamedArg(name = "FILE", defaultValue="-")
    public void setFilename(String filename) throws CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing file!");
        }
        if (!filename.equals("-")) {
            if (!new File(filename).exists()) {
                throw new CommandArgumentException("Missing file: "+filename);
            }
        }
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException, FilteringException {
        if (filename == null) {
            throw new CommandArgumentException("Missing input filename!");
        }

        FastqReader reader = Fastq.open(filename);

        if (verbose) {
            System.err.println("Reading file:" + filename);
        }

        TallyValues<String> tally = new TallyValues<String>();
        
        for (FastqRead read: reader) {
            String[] nameSplit = read.getName().split(":");
            String key = "";
            if (nameSplit.length < 6) {
                key = nameSplit[0]+":"+nameSplit[1];
            } else {
                // Casava 1.8+
                key = nameSplit[0]+":"+nameSplit[1]+":"+nameSplit[2]+":"+nameSplit[3];
            }
            
            if (read.getComment()!=null) {
                String[] commentSplit = read.getComment().split(":");
                key += "\t" + commentSplit[commentSplit.length-1];
            }
            
            tally.incr(key);
        }
        
        Map<String, Long> map = new HashMap<String, Long>();
        double total = 0;
        for (String k: tally.keySet()) {
            map.put(k, tally.getCount(k));
            total += tally.getCount(k);
        }

        List<Entry<String, Long>> entries = new ArrayList<Entry<String, Long>>(map.entrySet());
        
        Collections.sort(entries, new Comparator<Entry<String,Long>>(){
            @Override
            public int compare(Entry<String, Long> o1, Entry<String, Long> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }});
        
        int rgid=0;
        for (Entry<String, Long> entry: entries) {
            double frac = entry.getValue()/total;
            if (frac > minFraction) {
                if (writeSplitConfig) {
                    System.out.println(rgid+"\t"+entry.getKey());
                    rgid++;
                } else {
                    System.out.println(entry.getKey()+"\t"+entry.getValue()+"\t"+frac);
                }
            }
        }
        
    }
}
