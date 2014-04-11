package org.ngsutils.cli.fastq.filter;

import java.util.Iterator;

import org.ngsutils.fastq.FastqRead;

public interface Filter extends Iterator<FastqRead> {
	public long getTotal();
	public long getAltered();
	public long getRemoved();
}
