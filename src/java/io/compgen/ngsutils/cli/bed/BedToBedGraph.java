package io.compgen.ngsutils.cli.bed;

import java.io.IOException;
import java.io.OutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;
import io.compgen.ngsutils.support.SpanCounter;
import io.compgen.ngsutils.support.SpanCounter.PosCount;

@Command(name="bed-tobedgraph", desc="Convert a BED file to a coverage BedGraph file", category="bed")
public class BedToBedGraph extends AbstractOutputCommand {
    
    private String filename = null;
    private boolean includeZeros = false;
    
    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Include positions with zero depth in output", name="zero")
    public void setIncludeZeros(boolean includeZeros) {
        this.includeZeros = includeZeros;
    }

    public class BedGraphOutput {
    	private long curStart = -1;
    	private long lastPos = -1;
    	private int curCount = 0;
    	
    	private final OutputStream out;
    	private final boolean zero;
    	private final String ref;
    	
    	public BedGraphOutput(OutputStream out, boolean zero, String ref) {
    		this.out = out;
    		this.zero = zero;
    		this.ref = ref;
    	}
    	
    	public void update(long pos, int count) throws IOException {
			if (curStart == -1) {
				curStart = pos;
				curCount = count;
			}
			
			if (curCount != count) {
				if (curCount > 0 || zero) {
					out.write((ref + "\t" + curStart + "\t" + pos + "\t" + curCount + "\n").getBytes());
				}
				curStart = pos;
				curCount = count;
			}
    		lastPos = pos;
    	}

    	public void flush() throws IOException {
			out.write((ref + "\t" + curStart + "\t" + (lastPos+1) + "\t" + curCount + "\n").getBytes());
			curStart = -1;
			curCount = 0;
			lastPos = -1;
    	}
    	
    }
    
    @Exec
    public void exec() throws Exception {
    	
    	SpanCounter counter = null;
    	String curRef = null;
    	BedGraphOutput bgout = null;
    			
    	
        for (BedRecord record: IterUtils.wrap(BedReader.readFile(filename))) {
            GenomeSpan coord = record.getCoord();
            
            if (counter == null || !coord.ref.equals(curRef)) {
            	if (counter != null) {
            		while (counter.getCurPos() < counter.getMaxPos()) {
            			PosCount count = counter.pop();
//                        System.err.println("1 writing counter.pos: " + count.pos + " => " + count.count);

                        bgout.update(count.pos, count.count);
            		}
//                    System.err.println("2 flush");
            		bgout.flush();
            	}
            	
            	counter = new SpanCounter();
            	bgout = new BedGraphOutput(out, includeZeros, coord.ref);
            	curRef = coord.ref;
            }
//            System.err.println("3 Adding coord: " + coord);
        	counter.incr(coord.start, coord.end);
        	
    		while (counter.getCurPos() < coord.start) {
    			PosCount count = counter.pop();
//                System.err.println("4 writing counter.pos: " + count.pos + " => " + count.count);

                bgout.update(count.pos, count.count);
    		}
        }
		while (counter.getCurPos() < counter.getMaxPos()) {
			PosCount count = counter.pop();
//            System.err.println("5 writing counter.pos: " + count.pos + " => " + count.count);

            bgout.update(count.pos, count.count);
		}
//        System.err.println("6 flush");
		bgout.flush();
    }
}
