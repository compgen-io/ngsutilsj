package io.compgen.ngsutils.bam.filter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMRecord;

public abstract class AbstractBamFilter implements BamFilter, Iterable<SAMRecord> {
    protected BamFilter parent;
    protected boolean verbose; 
    
//    protected boolean requireOnePair = false; // if one member of a pair is kept, keep the other one too.
//    protected boolean requireBothPairs = false; // if one member of a pair fails, toss the other one too.
    
    protected boolean pairedKeep = false;
    protected boolean pairedRemove = false;
    
    protected boolean isfirst = true;
    protected long total = 0;
    protected long removed = 0;
    
    protected AbstractBamFilter(BamFilter parent, boolean verbose) {
        this.parent = parent;
        this.verbose = verbose;
    }
    
    public abstract boolean keepRead(SAMRecord read);
    
    protected SAMRecord nextRead = null;
//    protected Map<String, SAMRecord> pairs = new HashMap<String, SAMRecord>();
//    protected Set<String> keptpairs = new HashSet<String>();
    protected Deque<SAMRecord> nextReadBuffer = new ArrayDeque<SAMRecord>();;
    protected SAMRecord nextNextRead = null;
    
    public BamFilter getParent() {
        return parent;
    }
    
    protected void checkNext() {
        if (pairedKeep || pairedRemove) {
            while (nextRead == null) {
            	// For --pair-keep and --pair-remove, we keep track of all reads by their names
            	// So, all reads with the same name are pulled from the parent and processed together.
            	// Then if one read passes or fails, we apply the same to the rest of the set.
            	//
            	// GoodBuffer is what holds the list of good next reads.
            	
                if (nextReadBuffer.size()>0) {
                    nextRead = nextReadBuffer.removeFirst();
                    return;
                }
                        
                if (nextNextRead == null && parent.hasNext()) {
                    nextNextRead = parent.next();
                }
                if (nextNextRead == null) {
                    return;
                }

                nextReadBuffer.add(nextNextRead);
                nextNextRead = null;
                
                while (parent.hasNext()) {
                    SAMRecord curRead = parent.next();
                	total++;
                    if (curRead.getReadName().equals(nextReadBuffer.getFirst().getReadName())) {
                        nextReadBuffer.add(curRead);
                    } else {
                        nextNextRead = curRead;
                        break;
                    }
                }

                boolean failed = false;
                boolean passed = false;
                for (SAMRecord read: nextReadBuffer) {
                    if (keepRead(read)) {
                        passed = true;
                    } else {
                        failed = true;
                    }
                }

                if (pairedKeep) {
                    if (!passed) {
                        for (SAMRecord read: nextReadBuffer) {
                            failedRead(read);
                            removed++;
                        }
                        nextReadBuffer.clear();
                    }
                } else if (pairedRemove) {
                    if (failed) {
                        for (SAMRecord read: nextReadBuffer) {
                            failedRead(read);
                            removed++;
                        }
                    }
                }

                if (nextRead == null && nextReadBuffer.size()>0) {
                    nextRead = nextReadBuffer.removeFirst();
                    return;
                }
            }
        } else {
            while (nextRead == null) {
                // need to do this hasNext() check to notify parent that we might be done...
                // (this is needed for a progress meter to get notified)
    
                if (parent.hasNext()) {
                    nextRead = parent.next();
                }
                if (nextRead == null) {
                    break;
                }
                
                if (verbose) {
                    System.err.print("["+this.getClass().getSimpleName()+"] checking read: " + nextRead.getReadName());
                }
    
                total++;
                
                if (!keepRead(nextRead)) {
                    if (verbose) {
                        System.err.println(" REMOVED");
                    }
                    failedRead(nextRead);
                    removed++;
                    nextRead = null;
                } else {
                    if (verbose) {
                        System.err.println(" OK");
                    }
                }
            }
        }
    }

    public boolean hasNext() {
        if (isfirst) {
            checkNext();
            isfirst = false;
        }
        
        return nextRead != null;
    }

    public SAMRecord next() {
        if (isfirst) {
            checkNext();
            isfirst = false;
        }
        SAMRecord retval = nextRead;
        nextRead = null;
        checkNext();
        return retval;
    }

    public void remove() {
        // no-op
    }
    public Iterator<SAMRecord> iterator() {
        return this;
    }

    public long getTotal() {
        return total;
    }

    public long getRemoved() {
        return removed;
    }

    public SAMFileWriter getFailedWriter() {
        if (parent != null) {
            return parent.getFailedWriter();
        }
        return null;
    }
    
    private void failedRead(SAMRecord read) {
        if (getFailedWriter() != null && read != null) {
            getFailedWriter().addAlignment(read);
        }
    }
    
    @Override
    public void setPairedKeep() {
        this.pairedKeep = true;
        this.pairedRemove = false;
        
        if (parent != null) {
            parent.setPairedKeep();
        }
    }

    @Override
    public void setPairedRemove() {
        this.pairedKeep = false;
        this.pairedRemove = true;

        if (parent != null) {
            parent.setPairedRemove();
        }
    }

}
