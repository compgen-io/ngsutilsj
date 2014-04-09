package org.ngsutils.fastq.filter;

import java.util.Iterator;

import org.ngsutils.fastq.FastqRead;

public interface Filter extends Iterator<FastqRead> {
	public int getTotal();
	public int getAltered();
	public int getRemoved();
}
