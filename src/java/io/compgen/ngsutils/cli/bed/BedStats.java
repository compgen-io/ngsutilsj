package io.compgen.ngsutils.cli.bed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.TallyCounts;
import io.compgen.common.TallyValues;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

@Command(name="bed-stats", desc="Summary statistics for a BED file", category="bed")
public class BedStats extends AbstractOutputCommand {
    
    private String filename = null;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        long total = 0;
        long totalSize = 0;
        Set<String> refOrder = new HashSet<String>();
        List<String> refOrderList = new ArrayList<String>();
        
        TallyCounts sizeCounter = new TallyCounts();
        
        TallyValues<String> refCounter = new TallyValues<String>();
        
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(filename))) {
            total += 1;
            totalSize += record.getCoord().length();
            sizeCounter.incr(record.getCoord().length());
            if (!refOrder.contains(record.getCoord().ref)) {
                refOrder.add(record.getCoord().ref);
                refOrderList.add(record.getCoord().ref);
            }
            refCounter.incr(record.getCoord().ref);
        }
        
        System.out.println("Total number of regions:\t"+total);
        System.out.println("Total number of bases:\t"+totalSize);
        System.out.println("");
        System.out.println("Mean region size:\t"+sizeCounter.getMean());
        System.out.println("Median region size:\t"+sizeCounter.getMedian());
        System.out.println("Max region size:\t"+sizeCounter.getMax());
        System.out.println("Min region size:\t"+sizeCounter.getMin());
        System.out.println("");
        for (String k: refOrderList) {
            System.out.println(k+"\t"+refCounter.getCount(k));
        }
    }
}
