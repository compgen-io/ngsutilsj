package io.compgen.ngsutils.support.progress;


public class IncrementingStats implements ProgressStats {
    private long size;
    private long pos = 0;
    
    public IncrementingStats(long size) {
        this.size = size;
    }
    @Override
    public long size() {
        return size;
    }

    @Override
    public long position() {
        return pos ++;
    }
}
