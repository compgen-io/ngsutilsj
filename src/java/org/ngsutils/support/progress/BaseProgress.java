package org.ngsutils.support.progress;

import java.util.Date;

public class BaseProgress implements Progress {
    protected long total=0;
    protected long current = 0;
    
    protected Date startDate = null;
    protected long started = -1;
    protected long lastUpdate = -1;
    
    protected boolean done = false;

    protected String name = null;
    protected String msg = null;
    
    @Override
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public void start(long size) {
        this.total = size;
        this.started = System.currentTimeMillis();
        this.startDate = new Date();
    }

    public void update(long current) {
        update(current, null);
    }

    public void update(long current, String msg) {
        if (done) {
            return;
        }
        this.current = current;
        this.msg = msg;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    public long estRemainingSec() {
        if (started == -1 || lastUpdate == -1) {
            return -1;
        }

        double pct = pctComplete();
        long elapsedMillis = elapsedMilliSec();
        if (elapsedMillis == -1) {
            return -1;
        }
        
        return (long) ((elapsedMillis / pct) - elapsedMillis) / 1000;
    }

    public double pctComplete() {
        if (current == 0 || total ==0 ) {
            return 0.0;
        }
        return ((double) current) / total;
    }

    
    public long elapsedMilliSec() {
        if (started == -1 || lastUpdate == -1) {
            return -1;
        }
        
        return lastUpdate - started;
    }

    public static String secondsToString(long secs) {
        if (secs == -1) {
            return "";
        }

        long mins = 0;
        long hours = 0;
        
        if (secs >= 60) {
            mins = secs / 60;
            secs = secs % 60;
            
            if (mins >= 60) {
                hours = mins / 60;
                mins = mins % 60;
            }
        }
        
        String out = "";
        
        if (hours > 0) {
            out = String.format("%d:%02d:%02d", hours, mins, secs); 
        } else if (mins > 0) {
            out = String.format("%d:%02d", mins, secs); 
        } else {
            out = String.format("0:%02d", secs); 
        }
        return out;
    }

    @Override
    public void done() {
        this.done = true;
    }    
}
