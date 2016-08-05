package io.compgen.ngsutils.cli.kmer;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.Pair;
import io.compgen.common.StringLineReader;
import io.compgen.common.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

@Command(name="kmer-diff", desc="Calculate differences between two kmer files", category="kmer", experimental=true)
public class KmerDiff extends AbstractOutputCommand {
    private String refFile =  null;
    private String expFile =  null;

	public KmerDiff(){
	}

	@UnnamedArg(name="FILE1...")
	public void setFilenames(String[] filenames) throws CommandArgumentException {
	    if (filenames == null || filenames.length != 2) {
	        throw new CommandArgumentException("You must supply only two kmer input files");
	    }
        this.refFile = filenames[0];
        this.expFile = filenames[1];
	}


    @Exec
	public void exec() throws IOException, CommandArgumentException, InterruptedException {
        if (refFile == null || expFile == null) {
            throw new CommandArgumentException("You must supply two kmer input files");
        }
        
        long refTotal = 0;
        long expTotal = 0;

        int refCount = 0;
        int expCount = 0;
        int bothCount = 0;
        
        Map<String, Pair<Integer,Integer>> kmers = new TreeMap<String, Pair<Integer,Integer>>();
        
        StringLineReader slr1 = new StringLineReader(refFile);
        for (String line: IterUtils.wrap(slr1.progress())) {
            refCount++;
            String[] cols = StringUtils.strip(line).split("\t");
            Integer val = Integer.parseInt(cols[1]);
            refTotal += val;
            kmers.put(cols[0], new Pair<Integer,Integer>(val, 0));
        }
        slr1.close();
        
        StringLineReader slr2 = new StringLineReader(expFile);
        for (String line: IterUtils.wrap(slr2.progress())) {
            expCount++;
            String[] cols = StringUtils.strip(line).split("\t");
            Integer val = Integer.parseInt(cols[1]);
            expTotal += val;
            if (kmers.containsKey(cols[0])) {
                kmers.put(cols[0], new Pair<Integer,Integer>(kmers.get(cols[0]).one, val));
                bothCount++;
            } else {
                kmers.put(cols[0], new Pair<Integer,Integer>(0, val));
            }
        }
        slr2.close();
        
        System.err.println("Ref total  : "+refTotal);
        System.err.println("Exp total  : "+expTotal);
        System.err.println("");

        System.err.println("Total kmers: "+kmers.size());
        System.err.println("Ref only   : "+(refCount - bothCount));
        System.err.println("Exp only   : "+(expCount - bothCount));
        System.err.println("Overlapping: "+bothCount);

        
        for (Entry<String, Pair<Integer,Integer>> entry: kmers.entrySet()) {
            System.out.println(entry.getKey()+"\t"+entry.getValue().one+"\t"+entry.getValue().two);
        }
	}
}
