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
    protected Deque<SAMRecord> goodBuffer = new ArrayDeque<SAMRecord>();;
    protected SAMRecord nextNextRead = null;
    
    public BamFilter getParent() {
        return parent;
    }
    
    protected void checkNext() {
        if (pairedKeep || pairedRemove) {
            while (nextRead == null) {
                if (goodBuffer.size()>0) {
                    nextRead = goodBuffer.removeFirst();
                    return;
                }
                        
                if (nextNextRead == null && parent.hasNext()) {
                    nextNextRead = parent.next();
                }
                if (nextNextRead == null) {
                    return;
                }

                goodBuffer.add(nextNextRead);
                
                while (parent.hasNext()) {
                    SAMRecord curRead = parent.next();
                    if (curRead.getReadName().equals(nextNextRead.getReadName())) {
                        goodBuffer.add(curRead);
                    } else {
                        nextNextRead = curRead;
                        break;
                    }
                }

                boolean failed = false;
                boolean passed = false;
                for (SAMRecord read: goodBuffer) {
                    if (keepRead(read)) {
                        passed = true;
                    } else {
                        failed = true;
                    }
                }
                
                if (pairedKeep) {
                    if (!passed) {
                        for (SAMRecord read: goodBuffer) {
                            failedRead(read);
                        }
                        goodBuffer.clear();
                    }
                } else if (pairedRemove) {
                    if (failed) {
                        for (SAMRecord read: goodBuffer) {
                            failedRead(read);
                        }
                    }
                }
                
                if (nextRead == null && goodBuffer.size()>0) {
                    nextRead = goodBuffer.removeFirst();
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
