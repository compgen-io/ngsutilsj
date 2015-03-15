package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

import java.util.Iterator;

public interface FastqFilter extends Iterator<FastqRead> {
	public long getTotal();
	public long getAltered();
	public long getRemoved();
}
