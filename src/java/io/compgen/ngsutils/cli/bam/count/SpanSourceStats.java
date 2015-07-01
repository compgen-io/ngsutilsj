package io.compgen.ngsutils.cli.bam.count;

import io.compgen.common.progress.ProgressStats;



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
