package io.compgen.ngsutils.support.progress;

public interface Progress {
    public void start(long size);
    public void update(long current);
    public void update(long current, String msg);
    public void done();
    public void setName(String name);
}
