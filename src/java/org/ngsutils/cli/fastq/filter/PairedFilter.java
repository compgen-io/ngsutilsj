package org.ngsutils.cli.fastq.filter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.ngsutils.fastq.FastqRead;

public class PairedFilter implements Filter {
    private Iterator<FastqRead> parent;
    private boolean verbose=false;
    private boolean done = false;
    
    private final int PAIR_LEN = 2;

    private long total = 0;
    private long altered = 0;
    private long removed = 0;
    
    
    private List<FastqRead> buffer = new ArrayList<FastqRead>();
    FastqRead tmpRead = null;
        
    public PairedFilter(Iterable<FastqRead> parent, boolean verbose) {
        this.parent = parent.iterator();
        this.verbose  = verbose;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing unpaired reads");
        }
    }
    

    @Override
    public void remove() {
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getAltered() {
        return altered;
    }

    @Override
    public long getRemoved() {
        return removed;
    }

    @Override
    public boolean hasNext() {
        return !done;
    }

    @Override
    public FastqRead next() {
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] next()");
        }
       // If we have reads in the buffer, push them first
        if (!buffer.isEmpty()) {
            FastqRead readToReturn = buffer.remove(0);
            if (verbose) {
                System.err.println("["+this.getClass().getSimpleName()+"] pushing read: " + readToReturn.getName());
            }
            return readToReturn;
        }
        
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] tmpRead ? " + (tmpRead != null));
        }
        // Load up the straggler...
        String curName = null;
        if (tmpRead != null) {
            buffer.add(tmpRead);
            curName = tmpRead.getName();
            tmpRead = null;
        }

        // pull new reads until we don't match the curName
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] pulling from parent");
        }
        done = true;
        while (parent.hasNext()) {
            FastqRead read = parent.next();
            done = false;
            total++;
            if (verbose) {
                System.err.println("["+this.getClass().getSimpleName()+"] checking read: " + read.getName());
            }

            if (curName == null) {
                curName = read.getName();
            }
            
            if (curName.equals(read.getName())) {
                buffer.add(read);
            } else {
                tmpRead = read;
                break;
            }
        }
        
        if (buffer.size() < PAIR_LEN) {
            removed += buffer.size();
            buffer.clear();
        }
        
        if (!done) {
            return next();
        }
        return null;
    }
}
