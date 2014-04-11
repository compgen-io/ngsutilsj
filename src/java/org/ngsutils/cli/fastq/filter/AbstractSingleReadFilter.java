package org.ngsutils.cli.fastq.filter;

import java.util.Iterator;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.fastq.FastqRead;

public abstract class AbstractSingleReadFilter implements Filter {
	protected long total = 0;
	protected long altered = 0;
	protected long removed = 0;
	protected boolean isdone = false;
	protected boolean isfirst = false;
	protected boolean verbose = false;
	protected Iterator<FastqRead> parent;

	public AbstractSingleReadFilter(Iterable<FastqRead> parent, boolean verbose) {
		this.parent = parent.iterator();
		this.verbose = verbose;
		this.isdone = false;
		this.isfirst = true;
	}

	public long getTotal() {
		return total;
	}

	public long getAltered() {
		return altered;
	}

	public long getRemoved() {
		return removed;
	}

	protected FastqRead nextRead = null;

	public boolean hasNext() {
		return !isdone;
	}

	public void remove() {		
	}

	abstract protected FastqRead filterRead(FastqRead read) throws NGSUtilsException;
	
	private void checkNext() throws NGSUtilsException {
		while (parent.hasNext()) {
			FastqRead read = parent.next();
			if (verbose) {
				System.err.print("["+this.getClass().getSimpleName()+"] checking read: " + read.getName());
			}
			total++;
			int oldlen = read.getQual().length();
			nextRead = filterRead(read);
			if (nextRead == null) {
				if (verbose) {
					System.err.println(" REMOVED");
				}
				removed++;
			} else if (nextRead.getQual().length() < oldlen) {
				if (verbose) {
					System.err.println(" ALTERED");
				}
				altered++;
				break;
			} else {
				if (verbose) {
					System.err.println(" OK");
				}
				break;
			}
		}		
	}
	
	public FastqRead next(){
		try {
			if (isfirst) {
				checkNext();
				isfirst=false;
			}
			FastqRead retval = this.nextRead;
			if (retval!=null) {
				nextRead = null;
				checkNext();
			}
			if (nextRead == null) {
				isdone = true;
			}
			
			return retval;
		} catch (NGSUtilsException e) {
			throw new RuntimeException(e);
		}
	}
}