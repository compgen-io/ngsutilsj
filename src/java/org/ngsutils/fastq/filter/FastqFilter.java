package org.ngsutils.fastq.filter;

import java.util.Iterator;

import org.ngsutils.fastq.FastqRead;

public interface FastqFilter extends Iterator<FastqRead> {
	public long getTotal();
	public long getAltered();
	public long getRemoved();
}
