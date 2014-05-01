package org.ngsutils.cli.bam.count;

public interface SpanSource extends Iterable<Span> {
    public String[] getHeader();
}
