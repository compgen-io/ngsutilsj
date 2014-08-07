package org.ngsutils.junction;

public class JunctionStats {
    public final double controlPct;
    public final double expPct;
    public final double pctDiff;
    public final double tScore;
    public final int controlCount;
    public final int controlGroupSum;
    public final int expCount;
    public final int expGroupSum;
    public final double psd;
    public final double controlVar;
    public final double expVar;
    
    public JunctionStats(int controlCount, int controlGroupSum, int expCount, int expGroupSum) {
        this.controlCount = controlCount;
        this.controlGroupSum = controlGroupSum;
        this.expCount = expCount;
        this.expGroupSum = expGroupSum;
        
        if (controlGroupSum > 0) {
            controlPct = ((double)controlCount / controlGroupSum);
        } else {
            controlPct = 0;
        }
        
        if (expGroupSum > 0) {
            expPct = ((double)expCount / expGroupSum);
        } else {
            expPct = 0;
        }

        if (controlCount > 0) {
            controlVar = controlPct * (1-controlPct) / controlCount;
        } else {
            controlVar = 0;
        }
        if (expCount > 0) {
            expVar = expPct * (1-expPct) / expCount;
        } else {
            expVar = 0;
        }

        psd = Math.sqrt(controlVar + expVar);
        
        pctDiff = expPct - controlPct;
        tScore = pctDiff / psd;
     }
}