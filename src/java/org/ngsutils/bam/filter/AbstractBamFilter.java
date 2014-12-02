package org.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class AbstractBamFilter implements BamFilter, Iterable<SAMRecord> {
    protected BamFilter parent;
    protected boolean verbose; 
    
    protected boolean requireOnePair = false; // if one member of a pair is kept, keep the other one too.
    protected boolean requireBothPairs = false; // if one member of a pair fails, toss the other one too.
    
    protected boolean isfirst = true;
    protected long total = 0;
    protected long removed = 0;
    
    protected AbstractBamFilter(BamFilter parent, boolean verbose) {
        this.parent = parent;
        this.verbose = verbose;
    }
    
    public abstract boolean keepRead(SAMRecord read);
    
    protected SAMRecord nextRead = null;
    protected Map<String, SAMRecord> pairs = new HashMap<String, SAMRecord>();
    protected Set<String> keptpairs = new HashSet<String>();
    protected Deque<SAMRecord> goodBuffer = new ArrayDeque<SAMRecord>();
    
    public BamFilter getParent() {
        return parent;
    }
    
    protected void checkNext() {
        if (goodBuffer.size()>0) {
            nextRead = goodBuffer.removeFirst();
            return;
        }
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
            
            if (requireOnePair && nextRead.getReadPairedFlag()) {
                // we will keep pairs together. if one passes, both pass.
                if (keptpairs.contains(nextRead.getReadName())) {
                    // we already kept the first member, so this one passes by default.
                    keptpairs.remove(nextRead.getReadName());
                    total++;
                    if (verbose) {
                        System.err.println(" KEPT-BY-PAIR");
                    }
                } else if (keepRead(nextRead)) {
                    // either this is the first, or the last one failed, but this one passed.
                    total++;
                    if (verbose) {
                        System.err.println(" OK");
                    }

                    // look for the pair
                    if (pairs.containsKey(nextRead.getReadName())) {
                        // the pair exists, so we need to add them both to the good buffer.
                        total++;
                        goodBuffer.add(pairs.get(nextRead.getReadName()));
                        pairs.remove(nextRead.getReadName());
                        goodBuffer.add(nextRead);
                        nextRead = null;
                        break;
                    } else {
                        // the pair wasn't found, so this is the first read. just return it.
                        keptpairs.add(nextRead.getReadName());
                    }
                } else if (pairs.containsKey(nextRead.getReadName())) {
                    // we failed, if the pair also failed, reset and try again
                    pairs.remove(nextRead.getReadName());
                    nextRead = null;
                    removed++;
                    removed++;
                    if (verbose) {
                        System.err.println(" REMOVED");
                    }
                } else {
                    // we failed, and we are the first one to fail... so, maybe the pair will save us
                    pairs.put(nextRead.getReadName(), nextRead);
                    if (verbose) {
                        System.err.println(" FAILED-PENDING");
                    }
                    nextRead = null;
                }
            } else if (requireBothPairs && nextRead.getReadPairedFlag()) {
                // keep pairs together, if one fails, both fail.
                if (keptpairs.contains(nextRead.getReadName())) {
                    // our pair failed, so we fail.
                    keptpairs.remove(nextRead.getReadName());
                    nextRead = null;
                    removed++;
                    if (verbose) {
                        System.err.println(" PAIR-FAILED");
                    }
                } else {
                    if (!keepRead(nextRead)) {
                        // we failed... so our pair must fail
                        
                        if (pairs.containsKey(nextRead.getReadName())) {
                            // the pair passed already...
                            removed ++;
                            pairs.remove(nextRead.getReadName());
                            
                        } else {
                            // we are first, so prepare to fail the next one.
                            keptpairs.add(nextRead.getReadName());
                            removed++;
                        }
                        
                        nextRead = null;
                        if (verbose) {
                            System.err.println(" FAILED");
                        }
                    } else if (pairs.containsKey(nextRead.getReadName())) {
                        // we passed... and the prior one passed too!
                        total++;
                        total++;
    
                        goodBuffer.add(pairs.get(nextRead.getReadName()));
                        pairs.remove(nextRead.getReadName());
                        goodBuffer.add(nextRead);
                        nextRead = null;
    
                        if (verbose) {
                            System.err.println(" PAIR-PASSED");
                        }
                        break;                    
                    } else {
                        // we passed, but we are first so we have to wait for the next one
                        pairs.put(nextRead.getReadName(), nextRead);
                        nextRead = null;
                        if (verbose) {
                            System.err.println(" PASSED-PENDING");
                        }
                    }
                }
            } else {
                // pairs are handled separately... this means that the proper paired flag could be wrong.
                total++;
                if (!keepRead(nextRead)) {
                    if (verbose) {
                        System.err.println(" REMOVED");
                    }
                    removed++;
                    nextRead = null;
                } else {
                    if (verbose) {
                        System.err.println(" OK");
                    }
                }
            }
        }
        if (nextRead == null && goodBuffer.size()>0) {
            nextRead = goodBuffer.removeFirst();
            return;
        }
    }
    
    public void setRequireOnePair(boolean val) {
        this.requireOnePair = val;
    }

    public void setRequireBothPairs(boolean val) {
        this.requireBothPairs = val;
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

}
