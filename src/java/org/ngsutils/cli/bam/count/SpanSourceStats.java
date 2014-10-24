package org.ngsutils.cli.bam.count;

import org.ngsutils.support.progress.ProgressStats;


public class SpanSourceStats implements ProgressStats {
    private SpanSource source;
    
    public SpanSourceStats(SpanSource source) {
        this.source = source;
    }

    @Override
    public long size() {
        return source.size();
    }

    @Override
    public long position() {
        return source.position();
    }
}
